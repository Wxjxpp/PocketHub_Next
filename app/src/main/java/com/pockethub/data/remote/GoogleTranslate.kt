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

    private fun translateChunk(text: String, targetLang: String): String {
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
                return provider.translate(text, sl, targetLang)
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
    }

    /** Normalize "zh-CN"/"zh-TW" style codes for providers expecting bare "zh". */
    private fun baseCode(lang: String): String = lang.substringBefore('-')

    /** Google's official-ish web endpoint: GET /translate_a/single?client=gtx&… */
    private object GoogleProvider : TranslationProvider {
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
     * Note: it rejects chunks over ~500 bytes, so only used when Google+Lingva fail
     * and the chunk is small enough; large chunks surface the earlier errors.
     */
    private object MyMemoryProvider : TranslationProvider {
        override fun translate(text: String, sourceLang: String, targetLang: String): String {
            if (text.length > 450) {
                // Provider limit — don't send garbage requests; fail fast so any
                // earlier provider's error is what the caller sees.
                throw IOException("MyMemory: chunk too large")
            }
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
     * Split [text] into chunks of roughly [CHUNK_SIZE] characters,
     * breaking at paragraph boundaries (\n\n) when possible.
     */
    private fun splitIntoChunks(text: String): List<String> {
        if (text.length <= CHUNK_SIZE) return listOf(text)

        val paragraphs = text.split("\n\n")
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (para in paragraphs) {
            if (current.length + para.length + 2 > CHUNK_SIZE && current.isNotEmpty()) {
                chunks.add(current.toString())
                current.clear()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            // If a single paragraph exceeds CHUNK_SIZE, split by lines
            if (para.length > CHUNK_SIZE) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                    current.clear()
                }
                val lines = para.split("\n")
                for (line in lines) {
                    if (current.length + line.length + 1 > CHUNK_SIZE && current.isNotEmpty()) {
                        chunks.add(current.toString())
                        current.clear()
                    }
                    if (current.isNotEmpty()) current.append("\n")
                    if (line.length > CHUNK_SIZE) {
                        // Last resort: hard-split at character boundary
                        var remaining = line
                        while (remaining.length > CHUNK_SIZE) {
                            val breakAt = remaining.lastIndexOf(' ', CHUNK_SIZE)
                                .takeIf { it > 0 } ?: CHUNK_SIZE
                            chunks.add(remaining.substring(0, breakAt))
                            remaining = remaining.substring(breakAt).trimStart()
                        }
                        current.append(remaining)
                    } else {
                        current.append(line)
                    }
                }
            } else {
                current.append(para)
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks
    }
}
