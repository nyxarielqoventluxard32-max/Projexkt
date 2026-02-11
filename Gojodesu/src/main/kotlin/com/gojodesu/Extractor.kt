package com.gojodesu

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import org.jsoup.Jsoup

class KotakAjaibExtractor : ExtractorApi() {

    override val name = "KotakAjaib"
    override val mainUrl = "https://kotakajaib.me"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fixedUrl = if (url.startsWith("//")) "https:$url" else url

        val doc = app.get(
            fixedUrl,
            referer = referer ?: mainUrl
        ).document

        val videoSources = mutableListOf<Pair<String, String>>()

        doc.select("video source").forEach {
            val src = it.attr("src")
            val quality = it.attr("label").ifBlank { "HD" }
            if (src.isNotBlank()) {
                videoSources.add(src to quality)
            }
        }

        doc.select("script").forEach { script ->
            val data = script.data()
            Regex("""file\s*:\s*["'](.*?)["']""").findAll(data).forEach {
                val link = it.groupValues[1]
                if (link.startsWith("http")) {
                    videoSources.add(link to "HD")
                }
            }
        }

        if (videoSources.isEmpty()) {
            WebViewResolver(
                interceptUrl = { intercepted ->
                    if (intercepted.contains(".m3u8") || intercepted.contains(".mp4")) {
                        callback.invoke(
                            ExtractorLink(
                                name,
                                name,
                                intercepted,
                                referer ?: mainUrl,
                                Qualities.Unknown.value,
                                isM3u8 = intercepted.contains(".m3u8")
                            )
                        )
                    }
                }
            ).resolve(fixedUrl)
            return
        }

        videoSources.distinct().forEach { (videoUrl, quality) ->
            callback.invoke(
                ExtractorLink(
                    name,
                    "$name - $quality",
                    videoUrl,
                    referer ?: mainUrl,
                    getQualityFromName(quality),
                    isM3u8 = videoUrl.contains(".m3u8")
                )
            )
        }
    }
}