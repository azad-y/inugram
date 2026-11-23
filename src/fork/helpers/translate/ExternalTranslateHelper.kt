package desu.inugram.helpers.translate

import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.FileLog
import org.telegram.tgnet.TLRPC
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/** BYOK transports for Azure AI Translator and the OpenAI Responses API. */
internal object ExternalTranslateHelper {
    private val executor = Executors.newFixedThreadPool(2)
    private val calls = ConcurrentHashMap<Long, Future<*>>()

    fun translate(account: Int, token: Int, provider: String,
        texts: List<TLRPC.TL_textWithEntities>, targetLanguage: String,
        callback: (List<TLRPC.TL_textWithEntities>?) -> Unit) {
        val key = (account.toLong() shl 32) or (token.toLong() and 0xffffffffL)
        val finished = AtomicBoolean(false)
        val future = executor.submit {
            try {
                val values = when (provider) {
                    MICROSOFT -> translateMicrosoft(texts, targetLanguage)
                    OPENAI -> translateOpenAI(texts, targetLanguage)
                    else -> throw IOException("Unknown built-in translation provider")
                }
                if (finished.compareAndSet(false, true)) {
                    calls.remove(key)
                    callback(values.map { value -> TLRPC.TL_textWithEntities().apply {
                        text = value
                        entities = ArrayList()
                    } })
                }
            } catch (error: Exception) {
                if (finished.compareAndSet(false, true)) {
                    calls.remove(key)
                    FileLog.e("Built-in " + provider + " translation failed", error)
                    callback(null)
                }
            }
        }
        calls[key] = future
        if (finished.get()) calls.remove(key, future)
    }

    fun cancel(account: Int, token: Int): Boolean {
        val key = (account.toLong() shl 32) or (token.toLong() and 0xffffffffL)
        return calls.remove(key)?.cancel(true) ?: false
    }

    private fun translateMicrosoft(texts: List<TLRPC.TL_textWithEntities>, target: String): List<String> {
        val apiKey = TranslationSecrets.get("microsoft_key")
        if (apiKey.isBlank()) throw IOException("Microsoft Translator API key is not configured")
        val region = TranslationSecrets.get("microsoft_region")
        val connection = open("https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=" + encode(target), "POST")
        try {
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey)
            if (region.isNotBlank()) connection.setRequestProperty("Ocp-Apim-Subscription-Region", region)
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            val body = JSONArray().apply { texts.forEach { put(JSONObject().put("Text", it.text.toString())) } }.toString()
            val array = JSONArray(execute(connection, body))
            if (array.length() != texts.size) throw IOException("Microsoft Translator returned an unexpected result count")
            return (0 until array.length()).map { i ->
                array.getJSONObject(i).getJSONArray("translations").getJSONObject(0).getString("text")
            }
        } finally { connection.disconnect() }
    }

    private fun translateOpenAI(texts: List<TLRPC.TL_textWithEntities>, target: String): List<String> {
        val apiKey = TranslationSecrets.get("openai_key")
        if (apiKey.isBlank()) throw IOException("OpenAI API key is not configured")
        val model = TranslationSecrets.get("openai_model").ifBlank { "gpt-4.1-mini" }
        val prompt = buildString {
            append("Translate each input item into language code '").append(target)
                .append("'. Preserve meaning, tone, names, URLs and formatting. Return ONLY a JSON array of translated strings in the same order, with exactly ")
                .append(texts.size).append(" items. No explanations.\n")
            texts.forEachIndexed { index, item ->
                append(index + 1).append(". ").append(JSONObject.quote(item.text.toString())).append("\n")
            }
        }
        val connection = open("https://api.openai.com/v1/responses", "POST")
        try {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("model", model).put("input", prompt).toString()
            val response = JSONObject(execute(connection, body))
            val output = response.optJSONArray("output") ?: throw IOException("OpenAI response has no output")
            val outputText = StringBuilder()
            for (i in 0 until output.length()) {
                val content = output.optJSONObject(i)?.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val item = content.optJSONObject(j) ?: continue
                    if (item.optString("type") == "output_text") outputText.append(item.optString("text"))
                }
            }
            val result = JSONArray(outputText.toString().trim())
            if (result.length() != texts.size) throw IOException("OpenAI returned an unexpected result count")
            return (0 until result.length()).map { result.getString(it) }
        } finally { connection.disconnect() }
    }

    private fun open(url: String, method: String) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("Accept", "application/json")
        }

    private fun execute(connection: HttpURLConnection, body: String): String {
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val status = connection.responseCode
        val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw IOException("Translation HTTP " + status + ": " + response.take(500))
        return response
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    const val MICROSOFT = "builtin:microsoft"
    const val OPENAI = "builtin:openai"
}
