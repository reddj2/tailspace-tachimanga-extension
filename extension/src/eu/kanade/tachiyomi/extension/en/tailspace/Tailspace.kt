package eu.kanade.tachiyomi.extension.en.tailspace

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

@Source
abstract class Tailspace : ParsedHttpSource() {
    override val name = "Tailspace"
    override val supportsLatest = true
    override val baseUrl = "https://tailspace.com"

    override val client = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/browse?page=$page&sort=rating", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/browse?page=$page&sort=updated", headers)

    override fun popularMangaSelector() = "a[href^=/c/]"
    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = mangaFromAnchor(element)
    override fun latestUpdatesFromElement(element: Element): SManga = mangaFromAnchor(element)

    private fun mangaFromAnchor(anchor: Element): SManga = SManga.create().apply {
        title = anchor.text().trim()
        setUrlWithoutDomain(anchor.attr("href"))
        thumbnail_url = findCardImage(anchor)
    }

    private fun findCardImage(anchor: Element): String? {
        var node: Element? = anchor
        repeat(7) {
            val image = node?.selectFirst("img[src*=pics.tailspace.com], img[src*=/comics/]")
            if (image != null) return image.absUrl("src").ifBlank { image.attr("src") }
            node = node?.parent()
        }
        return null
    }

    override fun popularMangaNextPageSelector() = "a[href*=page]:matchesOwn(^Next$|^›$|^»$)"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/browse?page=$page&search=$encoded", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = mangaFromAnchor(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Filter duplicate anchors such as image + title links that point to the same comic.
    override fun popularMangaParse(response: Response): MangasPage = parseBrowse(response)
    override fun latestUpdatesParse(response: Response): MangasPage = parseBrowse(response)
    override fun searchMangaParse(response: Response): MangasPage = parseBrowse(response)

    private fun parseBrowse(response: Response): MangasPage {
        val document = response.asJsoup()
        val seen = linkedSetOf<String>()
        val manga = document.select(popularMangaSelector())
            .mapNotNull { anchor ->
                val href = anchor.attr("href")
                val title = anchor.text().trim()
                if (title.isBlank() || !seen.add(href)) null else mangaFromAnchor(anchor)
            }
        val currentPage = Regex("[?&]page=(\\d+)").find(response.request.url.toString())
            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val maxPage = document.select("button, a[href*=page]")
            .mapNotNull { it.text().trim().toIntOrNull() }
            .maxOrNull() ?: currentPage
        return MangasPage(manga, maxPage > currentPage)
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")?.text()?.trim().orEmpty()
        author = document.selectFirst("h1 + * a, a[href^=/u/], a[href^=/artist/]")?.text()?.trim()
        thumbnail_url = document.selectFirst("img[alt][src*=pics.tailspace.com]")?.absUrl("src")
        genre = document.select("a[href*=tag], [class*=tag]").map { it.text().trim() }.filter { it.isNotBlank() }.distinct().joinToString()
        status = if (document.text().contains("WIP", ignoreCase = true)) SManga.ONGOING else SManga.COMPLETED
        description = "Adult comic hosted on Tailspace."
    }

    override fun chapterListSelector() = "h1"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = "Read comic"
        url = element.ownerDocument()?.location()?.removePrefix(baseUrl).orEmpty()
        chapter_number = 1f
    }

    override fun pageListParse(document: Document): List<Page> {
        val location = document.location().substringBefore('?')
        val total = document.select("a[href*=page]").mapNotNull { it.text().trim().toIntOrNull() }.maxOrNull()
            ?: Regex("(\\d+)\\s*/\\s*(\\d+)").find(document.text())?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: 1

        val urls = linkedSetOf<String>()
        for (pageNumber in 1..total) {
            val pageDocument = if (pageNumber == 1) document else client.newCall(GET("$location?page=$pageNumber", headers)).execute().asJsoup()
            pageDocument.select("img[src*=pics.tailspace.com/comics/]").forEach { image ->
                val url = image.absUrl("src").ifBlank { image.attr("src") }
                if (!url.contains("thumbnail", ignoreCase = true)) urls += url
            }
        }
        return urls.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().set("Referer", "$baseUrl/").build())

    override fun getFilterList() = FilterList()
}
