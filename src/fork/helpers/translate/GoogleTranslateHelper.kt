package desu.inugram.helpers.translate

import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.telegram.messenger.FileLog
import org.telegram.tgnet.TLRPC
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Built-in Google Translate (GTX) transport. It does not require Telegram Premium
 * or a plugin/provider configuration.
 */
internal object GoogleTranslateHelper {
    private val client = OkHttpClient()
    private val calls = ConcurrentHashMap<Long, MutableList<Call>>()

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
        val pendingCalls = Collections.synchronizedList(mutableListOf<Call>())
        calls[key] = pendingCalls
        val results = arrayOfNulls<TLRPC.TL_textWithEntities>(texts.size)
        val remaining = AtomicInteger(texts.size)
        val finished = AtomicBoolean(false)

        fun finishFailure(error: Throwable) {
            if (!finished.compareAndSet(false, true)) return
            calls.remove(key, pendingCalls)
            synchronized(pendingCalls) { pendingCalls.forEach { it.cancel() } }
            FileLog.e("Built-in Google translation failed", error)
            callback(null)
        }

        texts.forEachIndexed { index, source ->
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("translate.googleapis.com")
                .addPathSegments("translate_a/single")
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", sourceLanguages.getOrNull(index)?.takeIf { it.isNotBlank() && it != "und" } ?: "auto")
                .addQueryParameter("tl", targetLanguage)
                .addQueryParameter("dt", "t")
                .addQueryParameter("q", source.text.toString())
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val call = client.newCall(request)
            pendingCalls.add(call)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    finishFailure(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw IOException("Google Translate HTTP ${response.code}: ${body.take(300)}")
                        }
                        val root = JSONArray(body)
                        val segments = root.optJSONArray(0) ?: throw IOException("Google Translate response has no segments")
                        val translated = StringBuilder()
                        for (i in 0 until segments.length()) {
                            val segment = segments.optJSONArray(i) ?: continue
                            val part = segment.optString(0)
                            if (part != "null") translated.append(part)
                        }
                        if (translated.isEmpty()) throw IOException("Google Translate returned an empty result")
                        results[index] = TLRPC.TL_textWithEntities().apply {
                            text = translated.toString()
                            // Entity offsets refer to the source text and are not valid after translation.
                            entities = ArrayList()
                        }
                        if (remaining.decrementAndGet() == 0 && finished.compareAndSet(false, true)) {
                            calls.remove(key, pendingCalls)
                            callback(results.map { it ?: TLRPC.TL_textWithEntities() })
                        }
                    } catch (e: Exception) {
                        finishFailure(e)
                    } finally {
                        response.close()
                    }
                }
            })
        }
    }

    fun cancel(account: Int, token: Int): Boolean {
        val key = (account.toLong() shl 32) or (token.toLong() and 0xffffffffL)
        val pending = calls.remove(key) ?: return false
        synchronized(pending) { pending.forEach { it.cancel() } }
        return true
    }
}
