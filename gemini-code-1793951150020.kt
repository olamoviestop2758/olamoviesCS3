package com.olamovies

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import org.jsoup.nodes.Element

class OlaMoviesProvider : MainAPI() {
    override var mainUrl = "https://v6.olamovies.mov"
    override var name = "OlaMovies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    companion object {
        private const val USER_EMAIL = "olamoviestop2758@gmail.com"
        private const val USER_PASS = "bahraich"
        var authCookies: MutableMap<String, String> = mutableMapOf()
    }

    private suspend fun ensureSession() {
        try {
            val ping = app.get(mainUrl, allowRedirects = true)
            val resolvedHost = Regex("https?://[^/]+").find(ping.url)?.value
            if (!resolvedHost.isNullOrEmpty() && resolvedHost != mainUrl) {
                mainUrl = resolvedHost
            }
            if (authCookies.isNotEmpty()) return

            val loginResponse = app.post(
                "$mainUrl/login",
                data = mapOf(
                    "email" to USER_EMAIL,
                    "username" to USER_EMAIL,
                    "log" to USER_EMAIL,
                    "password" to USER_PASS,
                    "pwd" to USER_PASS
                ),
                headers = mapOf("Referer" to mainUrl, "Origin" to mainUrl),
                allowRedirects = true
            )
            if (loginResponse.cookies.isNotEmpty()) {
                authCookies.putAll(loginResponse.cookies)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/movies/?page=" to "Latest Movies",
        "$mainUrl/tv-series/?page=" to "TV Series",
        "$mainUrl/tv-shows/?page=" to "TV Shows"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureSession()
        val doc = app.get(request.data + page, cookies = authCookies).document
        val items = doc.select("article, div.movie-card, div.post-item, a[href*='/movies/'], a[href*='/tv-series/']")
            .mapNotNull { it.toSearchResponse() }
            .distinctBy { it.url }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val anchor = if (this.tagName() == "a") this else this.selectFirst("a[href]")
        val href = fixUrlNull(anchor?.attr("href")) ?: return null
        if (!href.contains("/movies/") && !href.contains("/tv-series/") && !href.contains("/tv-shows/")) return null

        val title = this.selectFirst("h2, h3, h4, .title, .movie-title")?.text()?.trim()
            ?: anchor?.attr("title")?.trim() ?: return null
        val poster = fixUrlNull(this.selectFirst("img")?.attr("data-src") ?: this.selectFirst("img")?.attr("src"))
        val isSeries = href.contains("/tv-series/") || href.contains("/tv-shows/")

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureSession()
        val doc = app.get("$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}", cookies = authCookies).document
        return doc.select("article, div.movie-card, div.result-item, a[href*='/movies/']")
            .mapNotNull { it.toSearchResponse() }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureSession()
        val doc = app.get(url, cookies = authCookies).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: "OlaMovies Content"
        val poster = fixUrlNull(doc.selectFirst("meta[property='og:image']")?.attr("content") ?: doc.selectFirst("div.poster img")?.attr("src"))
        val plot = doc.selectFirst("meta[name='description']")?.attr("content") ?: doc.selectFirst("div.synopsis")?.text()?.trim()

        val releasePayloadList = mutableListOf<String>()
        doc.select("a[href*='drive.olamovies.download'], a[href*='gdflix'], button[data-link], div.release-row").forEach { row ->
            val rawLink = row.attr("href").ifEmpty { row.attr("data-link") }
            val rowMeta = row.text().trim()
            if (rawLink.isNotEmpty()) {
                releasePayloadList.add("${fixUrl(rawLink)}|||$rowMeta")
            }
        }
        if (releasePayloadList.isEmpty()) releasePayloadList.add("$url|||$title")

        val combinedData = releasePayloadList.distinct().joinToString("###")
        val isSeries = url.contains("/tv-series/") || url.contains("/tv-shows/")

        return if (isSeries) {
            val episodes = releasePayloadList.mapIndexed { idx, item ->
                val parts = item.split("|||")
                newEpisode(parts[0]) {
                    this.name = parts.getOrNull(1) ?: "Release ${idx + 1}"
                    this.episode = idx + 1
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster; this.plot = plot
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, combinedData) {
                this.posterUrl = poster; this.plot = plot
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        ensureSession()
        data.split("###").map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
            val parts = entry.split("|||")
            val link = parts[0].trim()
            val metaTag = parts.getOrNull(1)?.trim() ?: ""

            when {
                link.contains("workers.dev") -> callback.invoke(ExtractorLink(name, "OlaMovies Rocket Direct", link, "https://drive.olamovies.download/", Qualities.P1080.value, false))
                link.contains("drive.olamovies.download") -> OmDriveExtractor().getUrl(link, metaTag, subtitleCallback, callback)
                link.contains("gdflix") -> GdFlixExtractor().getUrl(link, metaTag, subtitleCallback, callback)
                else -> loadExtractor(link, mainUrl, subtitleCallback, callback)
            }
        }
        return true
    }
}