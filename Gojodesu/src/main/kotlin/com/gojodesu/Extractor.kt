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

        val links = mutableListOf<ExtractorLink>()

        val doc = app.get(url, referer = referer ?: mainUrl).document

        doc.select("video source").forEach { source ->
            val videoUrl = source.attr("src")
            if (videoUrl.isNotBlank()) {
                links.add(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = videoUrl,
                        referer = referer ?: mainUrl,
                        quality = Qualities.Unknown.value,
                        isM3u8 = videoUrl.contains(".m3u8")
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
                                referer = referer ?: mainUrl,
                                quality = Qualities.Unknown.value,
                                isM3u8 = videoUrl.contains(".m3u8")
                            )
                        )
                    }
                }
        }

        return links.ifEmpty { null }
    }
}
