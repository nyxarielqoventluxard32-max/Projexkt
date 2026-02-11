package com.gojodesu

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

class KotakAjaibExtractor : ExtractorApi() {

    override val name = "KotakAjaib"
    override val mainUrl = "https://kotakajaib.me"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink>? {

        val fixedUrl = if (url.startsWith("//")) "https:$url" else url
        val links = mutableListOf<ExtractorLink>()

        val headers = mapOf(
            "Referer" to (referer ?: mainUrl)
        )

        val doc = app.get(
            fixedUrl,
            referer = referer ?: mainUrl
        ).document

        doc.select("video source").forEach { source ->
            val videoUrl = source.attr("src")
            if (videoUrl.isNotBlank()) {
                links.add(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = videoUrl,
                        headers = headers
                    )
                )
            }
        }

        doc.select("script").forEach { script ->
            val data = script.data()
            Regex("""file\s*:\s*["'](.*?)["']""")
                .findAll(data)
                .forEach { match ->
                    val videoUrl = match.groupValues[1]
                    if (videoUrl.startsWith("http")) {
                        links.add(
                            newExtractorLink(
                                source = name,
                                name = name,
                                url = videoUrl,
                                headers = headers
                            )
                        )
                    }
                }
        }

        return links.ifEmpty { null }
    }
}
