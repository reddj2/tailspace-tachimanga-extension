package eu.kanade.tachiyomi.extension.en.tailspace

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Tailspace : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage =
        browse("$baseUrl/browse?page=$page&sort=rating", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        browse("$baseUrl/browse?page=$page&sort=updated", page)

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query.trim())
        }

        filters.filterIsInstance<TagFilter>()
            .flatMap { it.state }
            .forEach { tag ->
                when (tag.state) {
                    TAG_INCLUDE -> url.addQueryParameter("tag", tag.id.toString())
                    TAG_EXCLUDE -> url.addQueryParameter("excludeTag", tag.id.toString())
                }
            }

        return browse(url.build().toString(), page)
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Tap once to include a tag; tap twice to exclude it."),
        TagFilter(TAILSPACE_TAGS),
    )

    private suspend fun browse(url: String, page: Int): MangasPage {
        val document = client.get(url, headers).asJsoup()
        val seen = linkedSetOf<String>()

        val manga = document.select("a[href^=/c/]")
            .mapNotNull { anchor ->
                val href = anchor.attr("href")
                val title = anchor.text()

                if (title.isEmpty() || !seen.add(href)) {
                    null
                } else {
                    mangaFromAnchor(anchor)
                }
            }

        val maxPage = document.select("a[href*=page], button")
            .mapNotNull { it.text().toIntOrNull() }
            .maxOrNull()
            ?: page

        return MangasPage(manga, maxPage > page)
    }

    private fun mangaFromAnchor(anchor: Element): SManga = SManga.create().apply {
        title = anchor.text()
        url = anchor.attr("href")
        thumbnail_url = findCardImage(anchor)
    }

    private fun findCardImage(anchor: Element): String? {
        var node: Element? = anchor

        repeat(7) {
            val image = node?.selectFirst("img[src*=pics.tailspace.com], img[src*=/comics/]")
            if (image != null) {
                return image.absUrl("src").ifEmpty { image.attr("src") }
            }
            node = node?.parent()
        }

        return null
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.startsWith("/c/")) {
            return null
        }

        val document = client.get(url, headers).asJsoup()
        return parseDetails(document).apply {
            this.url = url.encodedPath
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), headers).asJsoup()
        val updatedManga = parseDetails(document).apply {
            url = manga.url
        }

        val chapterList = listOf(
            SChapter.create().apply {
                name = "Read comic"
                url = manga.url
                chapter_number = 1F
                scanlator = document.selectFirst("a[href^=/u/], a[href^=/artist/]")
                    ?.text()
            },
        )

        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")?.text()
            ?: throw Exception("Title not found")

        author = document.selectFirst("a[href^=/u/], a[href^=/artist/]")?.text()

        thumbnail_url = document.selectFirst(
            "img[alt][src*=pics.tailspace.com], img[src*=/comics/]",
        )?.let { image ->
            image.absUrl("src").ifEmpty { image.attr("src") }
        }

        genre = comicKeywords(document)
            ?: document.select(".rounded-full span.text-xs")
                .map { it.text() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString()

        status = if (document.text().lowercase().contains("wip")) {
            SManga.ONGOING
        } else {
            SManga.COMPLETED
        }

        description = document.selectFirst("meta[name=description]")
            ?.attr("content")
    }

    private fun comicKeywords(document: Document): String? {
        val comicJson = document.select("script[type=application/ld+json]")
            .map { it.data() }
            .firstOrNull { it.contains("\"@type\":\"ComicStory\"") }
            ?: return null

        return KEYWORDS_REGEX.find(comicJson)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?.takeIf { it.isNotBlank() }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val firstUrl = getChapterUrl(chapter)
        val firstDocument = client.get(firstUrl, headers).asJsoup()

        val totalPages = firstDocument.select("a[href*=page]")
            .mapNotNull { it.text().toIntOrNull() }
            .maxOrNull()
            ?: PAGE_COUNT_REGEX.find(firstDocument.text())
                ?.groupValues
                ?.getOrNull(2)
                ?.toIntOrNull()
            ?: 1

        val imageUrls = linkedSetOf<String>()

        for (pageNumber in 1..totalPages) {
            val document = if (pageNumber == 1) {
                firstDocument
            } else {
                val pageUrl = firstUrl.toHttpUrl().newBuilder()
                    .setQueryParameter("page", pageNumber.toString())
                    .build()

                client.get(pageUrl, headers).asJsoup()
            }

            document.select("img[src*=pics.tailspace.com/comics/]").forEach { image ->
                val imageUrl = image.absUrl("src").ifEmpty { image.attr("src") }

                if (!imageUrl.contains("thumbnail", ignoreCase = true)) {
                    imageUrls += imageUrl
                }
            }
        }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private class TagFilter(tags: List<TailspaceTag>) :
        Filter.Group<TagState>("Tags", tags.map(::TagState))

    private class TagState(tag: TailspaceTag) : Filter.TriState(tag.name) {
        val id = tag.id
    }

    companion object {
        private const val TAG_INCLUDE = 1
        private const val TAG_EXCLUDE = 2

        private val PAGE_COUNT_REGEX = Regex("""(\d+)\s*/\s*(\d+)""")
        private val KEYWORDS_REGEX = Regex(""""keywords"\s*:\s*"([^"]*)"""")
    }
}
