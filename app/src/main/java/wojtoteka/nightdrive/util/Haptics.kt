package wojtoteka.nightdrive.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice

/**
 * Small, safe haptics wrapper. All effects are short and honour [enabled]. When a gamepad with a
 * rumble motor is connected, the bigger effects (power-up, shield, crash, achievement) also fire
 * on the controller; the per-coin tick stays phone-only so the pad doesn't buzz constantly.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) { null }

    var enabled: Boolean = true

    fun tick() = oneShot(20, 140, rumble = false)
    fun light() = oneShot(30, 190)
    fun medium() = oneShot(50, 255)
    fun heavy() = oneShot(100, 255)
    fun success() = pattern(longArrayOf(0, 30, 70, 50))

    /** Two quick taps: something is coming that isn't on screen yet (a speeder). */
    fun warning() = pattern(longArrayOf(0, 22, 60, 22))

    /** Reaching combo tier [tier] (1-4): the higher the tier, the stronger the bump. */
    fun comboTier(tier: Int) = when {
        tier >= 4 -> pattern(longArrayOf(0, 35, 50, 45))
        tier == 3 -> medium()
        else -> light()
    }

    private fun oneShot(ms: Long, amplitude: Int, rumble: Boolean = true) {
        if (!enabled) return
        vibrator?.let { vibrateOne(it, ms, amplitude) }
        if (rumble) gamepadVibrator()?.let { vibrateOne(it, ms, amplitude) }
    }

    private fun vibrateOne(v: Vibrator, ms: Long, amplitude: Int) {
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Many devices ignore or mute custom amplitudes; DEFAULT_AMPLITUDE is the
                // reliable "actually noticeable" choice when there's no amplitude control.
                val amp = if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION") v.vibrate(ms)
            }
        } catch (_: Exception) {}
    }

    private fun pattern(timings: LongArray) {
        if (!enabled) return
        vibrator?.let { vibratePattern(it, timings) }
        gamepadVibrator()?.let { vibratePattern(it, timings) }
    }

    private fun vibratePattern(v: Vibrator, timings: LongArray) {
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION") v.vibrate(timings, -1)
            }
        } catch (_: Exception) {}
    }

    // ---- Controller rumble -----------------------------------------------------------------

    private var padVibrator: Vibrator? = null
    private var padDeviceId = -1

    /** Cached lookup of a connected gamepad's rumble motor; re-scans when the pad disconnects. */
    private fun gamepadVibrator(): Vibrator? {
        val cached = padVibrator
        if (cached != null && InputDevice.getDevice(padDeviceId) != null) return cached
        padVibrator = null
        padDeviceId = -1
        try {
            for (id in InputDevice.getDeviceIds()) {
                val dev = InputDevice.getDevice(id) ?: continue
                if (dev.isVirtual) continue
                val isGamepad = dev.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                    dev.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
                if (!isGamepad) continue
                val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dev.vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION") dev.vibrator
                }
                if (v.hasVibrator()) {
                    padVibrator = v
                    padDeviceId = id
                    return v
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
