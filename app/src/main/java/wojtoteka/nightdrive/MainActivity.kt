package wojtoteka.nightdrive

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.SystemClock
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import wojtoteka.nightdrive.engine.GamePhase
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.PadDir
import wojtoteka.nightdrive.ui.screens.NightDriveApp

/**
 * Single-activity host. Keeps the game fullscreen (immersive, edge-to-edge, screen-on) and forwards
 * lifecycle events to the [GameViewModel]. All UI and game logic lives in Compose + the engine.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    // Hardware controls: arrow keys, WASD, and an Xbox/generic gamepad's D-pad or left stick all
    // drive the same on-screen pad state as a touch, so they inherit its priority-over-slide rule.
    private val padKeyMap = mapOf(
        KeyEvent.KEYCODE_DPAD_UP to PadDir.UP,
        KeyEvent.KEYCODE_W to PadDir.UP,
        KeyEvent.KEYCODE_DPAD_DOWN to PadDir.DOWN,
        KeyEvent.KEYCODE_S to PadDir.DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to PadDir.LEFT,
        KeyEvent.KEYCODE_A to PadDir.LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to PadDir.RIGHT,
        KeyEvent.KEYCODE_D to PadDir.RIGHT,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !viewModel.isReady }

        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        // Debug-only screenshot staging: adb shell am start ... --ez demo true
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("demo", false) == true) {
            viewModel.requestDemo()
        }

        setContent { NightDriveApp(viewModel) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResume()
        hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Keyboard arrows/WASD and a gamepad's D-pad arrive as regular key events (Android synthesizes
    // DPAD_* from a controller's hat switch automatically). Only captured while actually driving,
    // so outside gameplay DPAD/arrows fall through to Compose and move focus around the menus
    // (and a hardware keyboard can still type the leaderboard nickname on the game-over screen).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val playing = isPlayingNow()
        val dir = padKeyMap[event.keyCode]
        if (dir != null) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (playing) viewModel.setPad(dir, true)
                KeyEvent.ACTION_UP -> viewModel.setPad(dir, false)
            }
            if (playing) return true
        }
        when (event.keyCode) {
            // Gamepad A = accept. Compose only treats DPAD_CENTER/Enter as a click, so re-dispatch.
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (playing) return true
                return super.dispatchKeyEvent(
                    KeyEvent(event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER, event.repeatCount)
                )
            }
            // Gamepad B = back (Compose BackHandlers route it per screen).
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
                return true
            }
            // Start / Escape = pause toggle during a run.
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ESCAPE -> {
                if (viewModel.hud.phase == GamePhase.PLAYING) {
                    if (event.action == KeyEvent.ACTION_UP) viewModel.togglePause()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // A gamepad's analog stick only arrives as continuous joystick motion, never key events.
    // While driving it steers; in the menus it is converted into synthetic DPAD presses so the
    // stick moves Compose focus just like the D-pad.
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            if (isPlayingNow()) {
                viewModel.setPad(PadDir.LEFT, x < -STICK_DEADZONE)
                viewModel.setPad(PadDir.RIGHT, x > STICK_DEADZONE)
                viewModel.setPad(PadDir.UP, y < -STICK_DEADZONE)
                viewModel.setPad(PadDir.DOWN, y > STICK_DEADZONE)
            } else {
                viewModel.setPad(PadDir.LEFT, false)
                viewModel.setPad(PadDir.RIGHT, false)
                viewModel.setPad(PadDir.UP, false)
                viewModel.setPad(PadDir.DOWN, false)
                stickToFocus(x, y)
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    // Edge-triggered with hysteresis: one focus move per stick flick, re-armed when it returns
    // toward centre - mirrors how consoles navigate menus with an analog stick.
    private var stickFocusX = 0
    private var stickFocusY = 0

    private fun stickToFocus(x: Float, y: Float) {
        val newX = axisState(x, stickFocusX)
        val newY = axisState(y, stickFocusY)
        if (newX != stickFocusX) {
            stickFocusX = newX
            if (newX != 0) sendDpad(if (newX > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
        }
        if (newY != stickFocusY) {
            stickFocusY = newY
            if (newY != 0) sendDpad(if (newY > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
        }
    }

    private fun axisState(v: Float, current: Int): Int = when {
        v > STICK_FOCUS_ON -> 1
        v < -STICK_FOCUS_ON -> -1
        v > -STICK_FOCUS_OFF && v < STICK_FOCUS_OFF -> 0
        else -> current
    }

    private fun sendDpad(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        super.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        super.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun isPlayingNow(): Boolean {
        val hud = viewModel.hud
        return hud.phase == GamePhase.PLAYING && !hud.paused
    }

    private companion object {
        const val STICK_DEADZONE = 0.35f
        const val STICK_FOCUS_ON = 0.6f
        const val STICK_FOCUS_OFF = 0.4f
    }
}
