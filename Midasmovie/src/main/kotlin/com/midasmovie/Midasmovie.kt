package com.midasmovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import java.util.*

suspend fun MainAPI.loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) {
    callback(
        newExtractorLink(
            source = "Default",
            name = "Default",
            url = url,
            type = ExtractorLinkType.VIDEO
        )
    )
}

fun String.httpsify(): String {
    return if (this.startsWith("http://")) this.replaceFirst("http://", "https://") else this
}

class Midasmovie : MainAPI() {
    override var mainUrl = "https://ssstik.tv"
    override var name = "Midasmovie🏵"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "movies/page/%d/" to "Latest Update",
        "tvshows/page/%d/" to "TV Series",
        "genre/action/page/%d/" to "Action",
        "genre/anime/page/%d/" to "Anime",
        "genre/comedy/page/%d/" to "Comedy",
        "genre/crime/page/%d/" to "Crime",
        "genre/drama/page/%d/" to "Drama",
        "genre/sci-fi-fantasy/page/%d/" to "Fantasy",
        "genre/horror/page/%d/" to "Horror",
        "genre/mystery/page/%d/" to "Mystery",
        "tag/china/page/%d/" to "China",
        "tag/japan/page/%d/" to "Japan",
        "tag/philippines/page/%d/" to "Philippines",
        "tag/thailand/page/%d/" to "Thailand"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1)
            "$mainUrl/${request.data.replace("page/%d/", "")}"
        else
            "$mainUrl/${request.data.format(page)}"

        val document = app.get(url.replace("//", "/").replace(":/", "://")).document
        val expectedType =
            if (request.data.contains("tvshows", true)) TvType.TvSeries else TvType.Movie

        val items = document.select(
            "article, div.ml-item, div.item, div.movie-item, div.film, div.item-infinite"
        ).mapNotNull { it.toSearchResult(expectedType) }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select(
            "article, div.ml-item, div.item, div.movie-item, div.film"
        ).mapNotNull { it.toSearchOnly() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst(
            "h1.entry-title, h1, .mvic-desc h3, .title"
        )?.text()
            ?.substringBefore("Season")
            ?.substringBefore("Episode")
            ?.substringBefore("(")
            ?.trim().orEmpty()

        val poster = document.selectFirst(
            ".sheader .poster img, figure.pull-left img, .poster img, " +
                    ".mvic-thumb img, img.wp-post-image, img"
        )?.fixPoster()?.let { fixUrl(it) }

        val description = document.selectFirst(
            "div[itemprop=description] > p, .wp-content > p, " +
                    ".entry-content > p, .desc p, .synopsis"
        )?.text()?.trim()

        val tags = document.select(
            "strong:contains(Genre) ~ a, .sgeneros a, .wp-tags a, .genre a, .genres a"
        ).eachText()

        val year = document.selectFirst(
            "strong:contains(Year) ~ a, .year, .release"
        )?.text()?.replace(Regex("\\D"), "")?.toIntOrNull()

        val rating = document.selectFirst(
            "span[itemprop=ratingValue], .dt_rating_vgs, .rating, .imdb"
        )?.text()?.toDoubleOrNull()

        val episodes = parseEpisodes(document)
        val tvType = if (episodes.isNotEmpty()) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                plot = description
                this.tags = tags
                this.year = year
                rating?.let { score = Score.from10(it) }
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster
                plot = description
                this.tags = tags
                this.year = year
                rating?.let { score = Score.from10(it) }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val mediaElements =
            document.select("div.pframe iframe, .dooplay_player iframe, iframe, video, source")

        mediaElements.forEach { element ->
            val link = when (element.tagName()) {
                "iframe" -> element.getIframeAttr()?.httpsify()
                "source" -> element.attr("src").httpsify()
                "video" -> element.attr("src").httpsify()
                else -> null
            }
            link?.let { loadExtractor(it, subtitleCallback, callback) }
        }

        document.select("track[kind=subtitles]").forEach { track ->
            val subUrl = track.attr("src")?.httpsify()
            if (!subUrl.isNullOrBlank()) {
                subtitleCallback(SubtitleFile(subUrl))
            }
        }

        return mediaElements.isNotEmpty()
    }

    private fun Element?.getIframeAttr(): String? {
        if (this == null) return null
        return this.attr("data-litespeed-src").takeIf { it.isNotBlank() }
            ?: this.attr("data-src").takeIf { it.isNotBlank() }
            ?: this.attr("src")
    }

    private fun Element?.fixPoster(): String? {
        if (this == null) return null

        if (this.hasAttr("srcset")) {
            val best = this.attr("srcset")
                .split(",")
                .map { it.trim().split(" ")[0] }
                .lastOrNull()
            if (!best.isNullOrBlank()) return best.fixImageQuality()
        }

        val dataSrc = when {
            this.hasAttr("data-lazy-src") -> this.attr("data-lazy-src")
            this.hasAttr("data-src") -> this.attr("data-src")
            else -> null
        }

        if (!dataSrc.isNullOrBlank()) return dataSrc.fixImageQuality()
        val src = this.attr("src")
        return if (src.isNotBlank()) src.fixImageQuality() else null
    }

    private fun String.fixImageQuality(): String {
        val regex =
            Regex("-\\d+x\\d+(?=\\.(webp|jpg|jpeg|png))", RegexOption.IGNORE_CASE)
        return replace(regex, "")
    }

    private fun parseEpisodes(document: org.jsoup.nodes.Document): List<Episode> {
        val containers = document.select(
            "div.episode-list, div.episodes, ul.episodes, div#episodes, div.eplister, div.seasons"
        )

        val links =
            if (containers.isNotEmpty())
                containers.select("a[href]")
            else
                document.select("a[href*=\"episode\"], a[href*=\"/eps\"], a[href*=\"/ep\"], a.episode")

        return links.mapIndexedNotNull { index, a ->
            val href = fixUrl(a.attr("href"))
            val name = a.text().ifBlank { "Episode ${index + 1}" }
            val epNum =
                Regex("E(p|ps)?\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(name)?.groupValues?.getOrNull(2)?.toIntOrNull()
                    ?: (index + 1)

            newEpisode(href) {
                this.name = name
                season = 1
                episode = epNum
            }
        }.distinctBy { it.data }
    }

    private fun Element.toSearchResult(expectedType: TvType? = null): SearchResponse? {
        val link = selectFirst("a[href]") ?: return null
        val href = fixUrl(link.attr("href"))

        val title = link.attr("title")
            .removePrefix("Permalink to:")
            .ifBlank {
                selectFirst("h1, h2, h3, h4, .title, .movie-title, .entry-title")
                    ?.text().orEmpty()
            }.trim()

        if (title.isBlank()) return null

        val posterUrl = selectFirst("img")?.fixPoster()?.let { fixUrl(it) }
        val quality =
            selectFirst(".quality, .gmr-quality-item, .gmr-qual, .q")
                ?.text()?.replace("-", "")

        val inferredType = expectedType ?: when {
            href.contains("tvshow", true) -> TvType.TvSeries
            selectFirst(".type-tv, .tv, .series") != null -> TvType.TvSeries
            else -> TvType.Movie
        }

        return if (inferredType == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = posterUrl
                quality?.let { addQuality(it) }
            }
        }
    }

    private fun Element.toSearchOnly(): SearchResponse? {
        val link = selectFirst("div.title a") ?: return null
        val href = fixUrl(link.attr("href"))
        val title = link.text().trim()
        val poster = selectFirst(".thumbnail a img")?.fixPoster()?.let { fixUrl(it) }

        val typeText =
            selectFirst(".thumbnail a span")?.text()?.lowercase(Locale.ROOT)
        val isMovie = typeText?.contains("movie") == true

        val rating =
            selectFirst(".meta .rating")?.text()
                ?.replace("IMDb", "")?.trim()?.toDoubleOrNull()

        return if (isMovie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                rating?.let { score = Score.from10(it) }
            }
        } else {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                rating?.let { score = Score.from10(it) }
            }
        }
    }
}
