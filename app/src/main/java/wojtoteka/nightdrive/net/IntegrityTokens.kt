package wojtoteka.nightdrive.net

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import wojtoteka.nightdrive.BuildConfig
import kotlin.coroutines.resume

/**
 * Google Play Integrity tokens for score submissions. Google signs a verdict about this install
 * (unmodified app from Google Play, genuine device) that only the ranking server can decode. Each
 * token is bound to one submission through its nonce.
 *
 * Asked only when a score is actually being submitted, so the daily quota goes a long way.
 */
class IntegrityTokens(context: Context) {
    private val manager: IntegrityManager? = try {
        IntegrityManagerFactory.create(context.applicationContext)
    } catch (_: Exception) {
        null
    }

    /**
     * A token bound to [nonce], or null when Play can't provide one (no Play Store, a build that
     * wasn't installed from Play, offline...). The server decides what a missing token means.
     */
    suspend fun request(nonce: String): String? {
        val m = manager ?: return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    val request = IntegrityTokenRequest.builder().setNonce(nonce)
                    // Only needed for builds distributed outside Play; set it in local.properties.
                    if (BuildConfig.PLAY_CLOUD_PROJECT > 0L) request.setCloudProjectNumber(BuildConfig.PLAY_CLOUD_PROJECT)
                    m.requestIntegrityToken(request.build())
                        .addOnSuccessListener { cont.resume(it.token()) }
                        .addOnFailureListener { cont.resume(null) }
                } catch (_: Exception) {
                    cont.resume(null)
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
