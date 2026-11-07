package com.olamovies

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class OmDriveExtractor : ExtractorApi() {
    override val name = "OM Drive"
    override val mainUrl = "https://drive.olamovies.download"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val doc = app.get(url, cookies = OlaMoviesProvider.authCookies).document
            val quality = getQualityFromName(referer ?: doc.text())

            doc.select("a[href*='workers.dev'], input[value*='workers.dev']").forEach { el ->
                val workerLink = el.attr("href").ifEmpty { el.attr("value") }
                if (workerLink.startsWith("http")) {
                    callback.invoke(
                        ExtractorLink(
                            name,
                            "OM Drive - Rocket Download",
                            workerLink,
                            mainUrl,
                            quality,
                            false
                        )
                    )
                }
            }

            doc.select("a:containsOwn(Unprotected link), a:containsOwn(Protected link), a[href*='vdlink']").forEach { el ->
                val dlUrl = el.attr("href")
                if (dlUrl.startsWith("http")) {
                    callback.invoke(
                        ExtractorLink(
                            name,
                            "OM Drive - Direct DL",
                            dlUrl,
                            url,
                            quality,
                            false
                        )
                    )
                }
            }

            doc.select("a[href*='gdflix']").forEach { el ->
                val gdUrl = el.attr("href")
                if (gdUrl.startsWith("http")) {
                    GdFlixExtractor().getUrl(gdUrl, referer ?: url, subtitleCallback, callback)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class GdFlixExtractor : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://new8.gdflix.io"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val res = app.get(url, allowRedirects = true)
            val doc = res.document
            val currentHost = Regex("https?://[^/]+").find(res.url)?.value ?: mainUrl
            val quality = getQualityFromName(referer ?: doc.title())

            doc.selectFirst("a:containsOwn(FAST CLOUD), a:containsOwn(ZIPDISK), a[href*='zfile']")?.let { btn ->
                val fcUrl = fixUrlNull(btn.attr("href"), currentHost)
                if (!fcUrl.isNullOrEmpty()) {
                    val fcDoc = app.get(fcUrl, referer = res.url).document
                    val finalLink = fcDoc.selectFirst("a:containsOwn(Download), a[href$='.mkv'], a[href$='.mp4']")?.attr("href") ?: fcUrl
                    callback.invoke(
                        ExtractorLink(
                            name,
                            "GDFlix - Fast Cloud",
                            fixUrl(finalLink, currentHost),
                            res.url,
                            quality,
                            false
                        )
                    )
                }
            }

            doc.selectFirst("a:containsOwn(LOGIN TO DL), a:containsOwn(10GBPS), a:containsOwn(INSTANT DL)")?.let { btn ->
                val dlUrl = fixUrlNull(btn.attr("href"), currentHost)
                if (!dlUrl.isNullOrEmpty()) {
                    callback.invoke(
                        ExtractorLink(
                            name,
                            "GDFlix - 10Gbps DL",
                            dlUrl,
                            res.url,
                            quality,
                            false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
