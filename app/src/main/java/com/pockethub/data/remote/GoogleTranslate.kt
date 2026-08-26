package com.pockethub.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Multi-provider free translation client (no API keys required).
 *
 * Providers, tried in order until one succeeds:
 *  1. Google `translate_a/single` (the endpoint behind translate.google.com)
 *  2. Lingva — open-source Google Translate proxy mirror
 *  3. MyMemory — community translation memory API
 *
 * A 429 (rate limit) or any error on one provider automatically falls through
 * to the next, so a single throttled endpoint no longer fails the whole
 * README translation.
 */
object GoogleTranslate {

    private const val CHUNK_SIZE = 4500 // safe limit per request

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000
    /** Hard ceiling for the whole translation so the UI can never spin forever. */
    private const val OVERALL_TIMEOUT_MS = 45_000L
    /** Max concurrent chunk requests (keeps us friendly to the free endpoints). */
    private const val MAX_CONCURRENT = 4
    /** Delay before retrying a rate-limited provider once, per chunk attempt. */
    private const val RATE_LIMIT_RETRY_DELAY_MS = 800L

    /**
     * Translate [text] into [targetLang] (e.g. "zh-CN", "en").
     *
     * Chunks are translated **concurrently** (capped at [MAX_CONCURRENT]) and the
     * whole operation is bounded by [OVERALL_TIMEOUT_MS]. Throws [IOException] on
     * total failure of every provider so the caller can surface it instead of
     * silently showing the original text.
     */
    suspend fun translate(text: String, targetLang: String): String {
        if (text.isBlank()) return text
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(OVERALL_TIMEOUT_MS) {
                    val chunks = splitIntoChunks(text)
                    if (chunks.size == 1) {
                        translateChunk(chunks[0], targetLang)
                    } else {
                        val semaphore = Semaphore(MAX_CONCURRENT)
                        coroutineScope {
                            chunks.map { chunk ->
                                async {
                                    semaphore.withPermit { translateChunk(chunk, targetLang) }
                                }
                            }.awaitAll()
                        }.joinToString("")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                throw IOException("Translation timed out — check your network and retry")
            }
        }
    }

    /**
     * Simple language detection: returns "zh" if >20% of non-whitespace chars
     * are CJK, otherwise "en".  Good enough for README content.
     */
    fun detectLanguage(text: String): String {
        val chars = text.filter { !it.isWhitespace() }
        if (chars.isEmpty()) return "en"
        val cjkCount = chars.count { ch ->
            val code = ch.code
            (code in 0x4E00..0x9FFF) || (code in 0x3400..0x4DBF) ||
                (code in 0x20000..0x2A6DF) || (code in 0xF900..0xFAFF)
        }
        return if (cjkCount.toFloat() / chars.length > 0.2f) "zh" else "en"
    }

    // ── internals ──────────────────────────────────────────────

    /**
     * Try one provider on [text]. If the provider's [TranslationProvider.maxChars]
     * limit is exceeded, re-split just for that provider and translate the
     * sub-chunks (still concurrently capped). This lets small-limit providers
     * (e.g. MyMemory) join the fallback chain even for large chunks instead of
     * failing fast.
     */
    private suspend fun translateWithProvider(
        provider: TranslationProvider,
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String {
        if (text.length <= provider.maxChars) return provider.translate(text, sourceLang, targetLang)
        // Provider limit exceeded — re-split just for it. Sub-chunks are translated
        // concurrently (capped) and joined in order.
        val subChunks = splitIntoChunks(text, provider.maxChars)
        return coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENT)
            subChunks.map { sub ->
                async {
                    semaphore.withPermit { provider.translate(sub, sourceLang, targetLang) }
                }
            }.awaitAll().joinToString("")
        }
    }

    private suspend fun translateChunk(text: String, targetLang: String): String {
        val sl = detectLanguage(text)
        // If already in target language, skip
        if ((targetLang.startsWith("zh") && sl == "zh") ||
            (targetLang == "en" && sl == "en")
        ) {
            return text
        }

        var lastError: Exception? = null
        for (provider in providers) {
            try {
                return translateWithProvider(provider, text, sl, targetLang)
            } catch (e: RateLimitException) {
                // Throttled: brief backoff then fall through to the next provider.
                lastError = e
                runCatching { Thread.sleep(RATE_LIMIT_RETRY_DELAY_MS) }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("All translation providers failed")
    }

    /** Ordered fallback chain — first success wins. */
    private val providers: List<TranslationProvider> = listOf(
        GoogleProvider,
        LingvaProvider("https://lingva.ml"),
        LingvaProvider("https://lingva.garudalinux.org"),
        MyMemoryProvider,
    )

    private class RateLimitException(message: String) : IOException(message)

    private interface TranslationProvider {
        fun translate(text: String, sourceLang: String, targetLang: String): String

        /** Max chars this provider accepts in one request (used for adaptive splitting). */
        val maxChars: Int get() = Int.MAX_VALUE
    }

    /** Normalize "zh-CN"/"zh-TW" style codes for providers expecting bare "zh". */
    private fun baseCode(lang: String): String = lang.substringBefore('-')

    /** Google's official-ish web endpoint: GET /translate_a/single?client=gtx&… */
    private object GoogleProvider : TranslationProvider {
        override val maxChars: Int get() = 4500

        override fun translate(text: String, sourceLang: String, targetLang: String): String {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded")
            val body = httpGet(url) { code ->
                if (code == 429 || code == 503) throw RateLimitException("Google translate HTTP $code")
                IOException("Google translate HTTP $code")
            }
            // Response: [[["translated","original",…],…], …]
            val arr = JSONArray(body)
            val segments = arr.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until segments.length()) {
                sb.append(segments.getJSONArray(i).getString(0))
            }
            return sb.toString()
        }
    }

    /**
     * Lingva — open-source Google Translate front-end with a clean JSON API:
     * GET {host}/api/v1/{source}/{target}/{query}
     * Multiple public mirrors exist; each instance is tried independently.
     */
    private class LingvaProvider(private val host: String) : TranslationProvider {
        /** URL-path based API — long encoded payloads risk 4xx on mirrors. */
        override val maxChars: Int get() = 1800

        override fun translate(text: String, sourceLang: String, targetLang: String): String {
            val path = URLEncoder.encode(text, "UTF-8")
                .replace("+", "%20")
            val url = URL("$host/api/v1/$sourceLang/${baseCode(targetLang)}/$path")
            val body = httpGet(url) { code ->
                if (code == 429 || code == 503) RateLimitException("Lingva HTTP $code")
                else IOException("Lingva ($host) HTTP $code")
            }
            // Response: {"translation": "…", …}
            return JSONObject(body).getString("translation")
        }
    }

    /**
     * MyMemory translation memory: GET /get?q=…&langpair=src|tgt
     * Free anonymous tier (~5k chars/day per IP); fine as an occasional fallback.
     * Chunks over its [maxChars] limit are re-split upstream, so every request
     * it receives is within budget.
     */
    private object MyMemoryProvider : TranslationProvider {
        /** Free anonymous tier rejects requests over ~500 bytes. */
        override val maxChars: Int get() = 450

        override fun translate(text: String, sourceLang: String, targetLang: String): String {
            val q = URLEncoder.encode(text, "UTF-8")
            val pair = "${baseCode(sourceLang)}|${baseCode(targetLang)}"
            val url = URL("https://api.mymemory.translated.net/get?q=$q&langpair=$pair")
            val body = httpGet(url) { code ->
                if (code == 429) RateLimitException("MyMemory HTTP $code")
                else IOException("MyMemory HTTP $code")
            }
            val obj = JSONObject(body)
            val status = obj.optInt("responseStatus", 500)
            if (status != 200) throw IOException("MyMemory status $status")
            return obj.getJSONObject("responseData").getString("translatedText")
        }
    }

    /** Minimal GET helper shared by all providers. Throws via [errorFor] on non-200. */
    private inline fun httpGet(url: URL, errorFor: (Int) -> Exception): String {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }
        return try {
            val code = conn.responseCode
            if (code == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                throw errorFor(code)
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Split [text] into chunks of at most [limit] characters.
     *
     * Text is decomposed into atomic units (paragraphs, then lines, then
     * hard-split runs for oversized lines) that keep their leading separators,
     * then greedily packed into chunks. Joining the returned chunks back with
     * "" therefore reconstructs the original layout — no blank lines lost at
     * chunk boundaries.
     */
    private fun splitIntoChunks(text: String, limit: Int = CHUNK_SIZE): List<String> {
        if (text.length <= limit) return listOf(text)

        val units = mutableListOf<String>()
        val paragraphs = text.split("\n\n")
        for ((pi, para) in paragraphs.withIndex()) {
            val paraPrefix = if (pi == 0) "" else "\n\n"
            if (paraPrefix.length + para.length <= limit) {
                units.add(paraPrefix + para)
                continue
            }
            val lines = para.split("\n")
            for ((li, line) in lines.withIndex()) {
                val prefix = if (li == 0) paraPrefix else "\n"
                var rest = prefix + line
                if (rest.length <= limit) {
                    units.add(rest)
                    continue
                }
                // Oversized single line: hard-split at word boundaries.
                while (rest.length > limit) {
                    val breakAt = rest.lastIndexOf(' ', limit)
                    if (breakAt > 0) {
                        // Keep the separating space on the left chunk so joining
                        // the pieces back reconstructs the original text.
                        units.add(rest.substring(0, breakAt + 1))
                        rest = rest.substring(breakAt + 1)
                    } else {
                        units.add(rest.substring(0, limit))
                        rest = rest.substring(limit)
                    }
                }
                if (rest.isNotEmpty()) units.add(rest)
            }
        }

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (unit in units) {
            if (current.isNotEmpty() && current.length + unit.length > limit) {
                chunks.add(current.toString())
                current.setLength(0)
            }
            current.append(unit)
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks
    }
}
