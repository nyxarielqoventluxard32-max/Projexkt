package com.midasmovie

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import org.jsoup.nodes.Document
import java.util.*

class PlayCinematic : ExtractorApi() {
    override val name = "PlayCinematic"
    override val mainUrl = "https://playcinematic.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "*/*"
        )

        val id = url.substringAfterLast("/")
        val streamUrl = "$mainUrl/stream/k/$id"

        val resp = app.get(
            streamUrl,
            referer = url,
            headers = headers,
            allowRedirects = false
        )

        val location = resp.headers["Location"] ?: return

        if (location.contains(".m3u8")) {
            generateM3u8(
                name,
                location,
                referer = url,
                headers = headers
            ).forEach(callback)
        } else {
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = location,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = url
                    this.headers = headers
                    this.quality = parseQuality(location)
                }
            )
        }

        val doc = app.get(url, headers = headers).document
        extractSubtitles(doc, subtitleCallback)
    }

    private fun extractSubtitles(
        document: Document,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        document.select("script").forEach { script ->
            val data = script.data()
            if (data.contains("eval(function(p,a,c,k,e,d)")) {
                val unpacked = getAndUnpack(data)

                val match = Regex(
                    "\"tracks\"\\s*:\\s*\\[(.*?)]",
                    RegexOption.DOT_MATCHES_ALL
                ).find(unpacked)?.groupValues?.get(1) ?: return

                val json = "[$match]"

                val type = object : TypeToken<List<Tracks>>() {}.type
                val tracks: List<Tracks> =
                    try {
                        Gson().fromJson(json, type)
                    } catch (e: Exception) {
                        return
                    }

                tracks.forEach { track ->
                    subtitleCallback(
                        SubtitleFile(
                            getLanguage(track.label),
                            toAbsoluteUrl(mainUrl, track.file)
                        )
                    )
                }
            }
        }
    }

    private fun parseQuality(url: String): Int {
        val match = Regex("(\\d{3,4})p").find(url)
        return match?.groupValues?.get(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private fun getLanguage(str: String?): String {
        val s = str?.lowercase(Locale.ROOT) ?: return "Unknown"
        return when {
            "indo" in s || s == "id" -> "Indonesian"
            "eng" in s || s == "en" -> "English"
            else -> str
        }
    }

    private fun toAbsoluteUrl(base: String, path: String): String {
        return if (path.startsWith("http")) path else base + path
    }
}

data class Tracks(
    val file: String,
    val label: String
)
