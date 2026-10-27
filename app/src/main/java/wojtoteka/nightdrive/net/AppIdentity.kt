package wojtoteka.nightdrive.net

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/** Facts about this install that the ranking server checks before it accepts a score. */
object AppIdentity {
    private val UNKNOWN = "0".repeat(64)

    /**
     * SHA-256 (lowercase hex) of the certificate this APK is signed with. A repackaged mod has to be
     * re-signed with another key, so its digest differs from the one the server allows. Returns
     * all zeros if the system won't tell (the server then refuses it once the check is on).
     */
    fun signingCertSha256(context: Context): String = try {
        val pm = context.packageManager
        val pkg = context.packageName
        val signers: Array<Signature>? = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
                    .signingInfo?.apkContentsSigners
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            else ->
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }
        val cert = signers?.firstOrNull()?.toByteArray()
        if (cert == null) UNKNOWN
        else MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        UNKNOWN
    }
}
