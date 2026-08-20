package wojtoteka.nightdrive.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import wojtoteka.nightdrive.BuildConfig
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** One row of the Top 10. */
data class LeaderboardEntry(val rank: Int, val name: String, val score: Int)

/** UI state for the leaderboard screen. */
sealed interface LeaderboardState {
    data object Loading : LeaderboardState
    data class Success(val entries: List<LeaderboardEntry>) : LeaderboardState
    data object Offline : LeaderboardState
    data class Error(val message: String) : LeaderboardState
}

/** Result of submitting a score. */
data class SubmitResult(val accepted: Boolean, val rank: Int?, val qualified: Boolean)

/** State of a score submission, for the game-over UI. */
sealed interface SubmitState {
    data object Idle : SubmitState
    data object Submitting : SubmitState
    data class Done(val rank: Int?) : SubmitState
    data object Failed : SubmitState
    data object Offline : SubmitState
}

/**
 * Talks to the Night Drive ranking API. All calls are suspend + on [Dispatchers.IO] and throw on
 * any network/parse problem so the caller can present an "offline" state. Score submissions are
 * signed with an HMAC shared secret so the server can reject forged payloads.
 */
class LeaderboardApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val apiKey: String = BuildConfig.API_KEY,
    private val secret: String = BuildConfig.LEADERBOARD_SECRET,
) {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // Identify the app clearly (helps Cloudflare WAF rules allow-list this client).
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "NightDrive-Android/${BuildConfig.VERSION_NAME}")
                    .header("Accept", "application/json")
                    .build()
            )
        }
        .build()

    suspend fun fetchTop(): List<LeaderboardEntry> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/api/v1/leaderboard").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val arr = JSONObject(body).getJSONArray("entries")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LeaderboardEntry(o.getInt("rank"), o.getString("name"), o.getInt("score"))
            }
        }
    }

    /** Opens a play session at the start of a run. Returns the session id, or null on failure. */
    suspend fun startSession(): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/v1/session")
                .header("x-api-key", apiKey)
                .post("{}".toRequestBody(json))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                JSONObject(resp.body?.string().orEmpty()).getString("sessionId")
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun submit(sessionId: String, name: String, score: Int): SubmitResult =
        withContext(Dispatchers.IO) {
            val clientTime = System.currentTimeMillis()
            val nonce = randomNonce()
            val signature = hmac("$sessionId|$name|$score|$clientTime|$nonce")
            val payload = JSONObject()
                .put("sessionId", sessionId)
                .put("name", name)
                .put("score", score)
                .put("clientTime", clientTime)
                .put("nonce", nonce)
                .put("signature", signature)
            val req = Request.Builder()
                .url("$baseUrl/api/v1/scores")
                .header("x-api-key", apiKey)
                .post(payload.toString().toRequestBody(json))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext SubmitResult(false, null, false)
                val o = JSONObject(body)
                SubmitResult(
                    accepted = o.optBoolean("accepted", false),
                    rank = if (o.has("rank")) o.getInt("rank") else null,
                    qualified = o.optBoolean("qualified", false),
                )
            }
        }

    private fun hmac(message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(message.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
