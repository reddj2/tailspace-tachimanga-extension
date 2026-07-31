import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tailspace"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "Tailspace"
        lang = "en"
        baseUrl = "https://tailspace.com"
        id = 4704682754071289646L
    }
}
