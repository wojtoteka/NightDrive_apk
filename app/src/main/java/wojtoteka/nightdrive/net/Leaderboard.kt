package wojtoteka.nightdrive.net

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import wojtoteka.nightdrive.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** One row of the Top 10. */
data class LeaderboardEntry(val rank: Int, val name: String, val score: Int)

/** Which ranking: the all-time Top 10 or today's daily route. */
enum class Board { GLOBAL, DAILY }

/** UI state for the leaderboard screen. */
sealed interface LeaderboardState {
    data object Loading : LeaderboardState
    data class Success(val entries: List<LeaderboardEntry>) : LeaderboardState
    data object Offline : LeaderboardState
    data class Error(val message: String) : LeaderboardState
}

/** Result of submitting a score; [error] is the server's error code when it was refused. */
data class SubmitResult(val accepted: Boolean, val rank: Int?, val qualified: Boolean, val error: String? = null)

/** State of a score submission, for the game-over UI. */
sealed interface SubmitState {
    data object Idle : SubmitState
    data object Submitting : SubmitState
    data class Done(val rank: Int?) : SubmitState
    data object Failed : SubmitState
    data object Offline : SubmitState
    /** Someone else owns that nickname - the entry field stays open for another one. */
    data object NameTaken : SubmitState
    /** The server couldn't verify this copy of the game (modified, re-signed or not from Play). */
    data object Unverified : SubmitState
    /** The server no longer accepts scores from this version. */
    data object UpdateRequired : SubmitState
}

/**
 * Talks to the Night Drive ranking API. All calls are suspend + on [Dispatchers.IO] and throw on
 * any network/parse problem so the caller can present an "offline" state.
 *
 * Scores go through the v2 endpoint: the HMAC signature covers the board, the app's versionCode,
 * the SHA-256 of the certificate the APK is signed with and a per-install player id (nickname
 * ownership). When Google Play can provide one, a Play Integrity token bound to that exact
 * signed message is attached, which a modified build can't produce.
 */
class LeaderboardApi(
    context: Context,
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val apiKey: String = BuildConfig.API_KEY,
    private val secret: String = BuildConfig.LEADERBOARD_SECRET,
) {
    private val certDigest = AppIdentity.signingCertSha256(context)
    private val integrity = IntegrityTokens(context)

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

    suspend fun fetchTop(): List<LeaderboardEntry> = fetchEntries("$baseUrl/api/v1/leaderboard")

    /** Top 10 of the daily route of UTC day [day] (the same day number the game seeds it with). */
    suspend fun fetchDaily(day: Long): List<LeaderboardEntry> = fetchEntries("$baseUrl/api/v2/daily?day=$day")

    private suspend fun fetchEntries(url: String): List<LeaderboardEntry> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            parseEntries(JSONObject(body).getJSONArray("entries"))
        }
    }

    private fun parseEntries(arr: JSONArray): List<LeaderboardEntry> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        LeaderboardEntry(o.getInt("rank"), o.getString("name"), o.getInt("score"))
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

    /**
     * Submits [score] to the all-time board, or to the daily route of UTC day [dailyDay].
     * [playerId] is this install's random id: the server lets only its first owner use a nickname.
     */
    suspend fun submit(sessionId: String, name: String, score: Int, playerId: String, dailyDay: Long? = null): SubmitResult {
        val clientTime = System.currentTimeMillis()
        val nonce = randomNonce()
        val board = if (dailyDay == null) "global" else "daily"
        val appVersion = BuildConfig.VERSION_CODE
        // Must match scoreMessageV2 on the server, field for field.
        val message = listOf(
            "v2", sessionId, board, dailyDay?.toString() ?: "", name, score, clientTime, nonce,
            appVersion, certDigest, playerId,
        ).joinToString("|")
        val integrityToken = integrity.request(integrityNonce(message))
        return withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("sessionId", sessionId)
                .put("board", board)
                .put("name", name)
                .put("score", score)
                .put("clientTime", clientTime)
                .put("nonce", nonce)
                .put("appVersion", appVersion)
                .put("certDigest", certDigest)
                .put("playerId", playerId)
                .put("signature", hmac(message))
            if (dailyDay != null) payload.put("day", dailyDay)
            if (integrityToken != null) payload.put("integrityToken", integrityToken)
            val req = Request.Builder()
                .url("$baseUrl/api/v2/scores")
                .header("x-api-key", apiKey)
                .post(payload.toString().toRequestBody(json))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val o = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                if (!resp.isSuccessful) {
                    return@withContext SubmitResult(false, null, false, o.optString("error").ifEmpty { "http_${resp.code}" })
                }
                SubmitResult(
                    accepted = o.optBoolean("accepted", false),
                    rank = if (o.has("rank")) o.getInt("rank") else null,
                    qualified = o.optBoolean("qualified", false),
                )
            }
        }
    }

    private fun hmac(message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(message.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** Play Integrity nonce for [message]: base64url SHA-256, the same value the server expects. */
    private fun integrityNonce(message: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(message.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
