package desu.inugram.helpers.translate

import org.json.JSONArray
import org.telegram.messenger.FileLog
import org.telegram.tgnet.TLRPC
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Built-in Google Translate (GTX) transport. It does not require Telegram Premium
 * or a plugin/provider configuration.
 */
internal object GoogleTranslateHelper {
    private class Call {
        val futures: MutableList<Future<*>> = Collections.synchronizedList(mutableListOf())

        /** set once, by whichever of success, failure or cancel comes first */
        val finished = AtomicBoolean(false)

        fun cancelFutures() = synchronized(futures) { futures.forEach { it.cancel(true) } }
    }

    private val executor = Executors.newFixedThreadPool(4)
    private val calls = ConcurrentHashMap<Long, Call>()

    fun translate(
        account: Int,
        token: Int,
        texts: List<TLRPC.TL_textWithEntities>,
        sourceLanguages: List<String?>,
        targetLanguage: String,
        callback: (List<TLRPC.TL_textWithEntities>?) -> Unit,
    ) {
        if (texts.isEmpty()) {
            callback(emptyList())
            return
        }

        val key = (account.toLong() shl 32) or (token.toLong() and 0xffffffffL)
        val call = Call()
        calls[key] = call
        val results = arrayOfNulls<TLRPC.TL_textWithEntities>(texts.size)
        val remaining = AtomicInteger(texts.size)

        fun finishFailure(error: Throwable) {
            if (!call.finished.compareAndSet(false, true)) return
            calls.remove(key, call)
            call.cancelFutures()
            FileLog.e("Built-in Google translation failed", error)
            callback(null)
        }

        texts.forEachIndexed { index, source ->
            val future = executor.submit {
                var connection: HttpURLConnection? = null
                try {
                    if (call.finished.get()) return@submit
                    val sourceLanguage = sourceLanguages.getOrNull(index)
                        ?.takeIf { it.isNotBlank() && it != "und" } ?: "auto"
                    val query = listOf(
                        "client" to "gtx",
                        "sl" to sourceLanguage,
                        "tl" to targetLanguage,
                        "dt" to "t",
                    ).joinToString("&") { (name, value) -> "$name=${encode(value)}" }
                    // the text goes in the body: a long message does not fit in a url
                    val form = "q=${encode(source.text.toString())}".toByteArray(StandardCharsets.UTF_8)
                    connection = (URL("https://translate.googleapis.com/translate_a/single?$query")
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        setFixedLengthStreamingMode(form.size)
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                        connectTimeout = 15000
                        readTimeout = 20000
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                    }
                    connection.outputStream.use { it.write(form) }
                    val status = connection.responseCode
                    val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (status !in 200..299) {
                        throw IOException("Google Translate HTTP $status: ${body.take(300)}")
                    }

                    val segments = JSONArray(body).optJSONArray(0)
                        ?: throw IOException("Google Translate response has no segments")
                    val translated = StringBuilder()
                    for (i in 0 until segments.length()) {
                        val segment = segments.optJSONArray(i) ?: continue
                        val part = segment.optString(0)
                        if (part != "null") translated.append(part)
                    }
                    if (translated.isEmpty()) throw IOException("Google Translate returned an empty result")

                    results[index] = TLRPC.TL_textWithEntities().apply {
                        text = translated.toString()
                        // Source entity offsets are invalid after translation.
                        entities = ArrayList()
                    }
                    if (remaining.decrementAndGet() == 0 && call.finished.compareAndSet(false, true)) {
                        calls.remove(key, call)
                        callback(results.map { it ?: TLRPC.TL_textWithEntities() })
                    }
                } catch (error: Exception) {
                    finishFailure(error)
                } finally {
                    connection?.disconnect()
                }
            }
            call.futures.add(future)
            if (call.finished.get()) future.cancel(true)
        }
    }

    fun cancel(account: Int, token: Int): Boolean {
        val key = (account.toLong() shl 32) or (token.toLong() and 0xffffffffL)
        val call = calls.remove(key) ?: return false
        // a request already on the wire cannot be interrupted, this keeps its answer from being delivered
        call.finished.set(true)
        call.cancelFutures()
        return true
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
