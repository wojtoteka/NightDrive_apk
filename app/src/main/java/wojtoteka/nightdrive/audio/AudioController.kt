package wojtoteka.nightdrive.audio

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The four in-game sound effects, mapped to their bundled asset files. */
enum class Sfx(val asset: String, val defaultVolume: Float) {
    COIN("sfx/coins.wav", 1.0f),
    POINT("sfx/point.wav", 0.8f),
    EXPLOSION("sfx/explosion.wav", 0.55f),
    DEFEAT("sfx/defeat.wav", 0.9f),
}

/**
 * Central audio: a [SoundPool] for low-latency SFX and a [MediaPlayer] for looping music.
 * All calls are safe to make regardless of load state; toggles honour the user's settings.
 *
 * The music reacts to the game: the frame loop sets a target volume and tempo every frame
 * ([setMusicTarget]) and [tickMusic] eases toward it, so changes never jump.
 */
class AudioController(private val context: Context) {

    private val soundPool: SoundPool
    private val soundIds = HashMap<Sfx, Int>()
    private var loaded = false

    private var music: MediaPlayer? = null
    private var musicPrepared = false

    private var musicVolume = MUSIC_VOLUME
    private var targetVolume = MUSIC_VOLUME
    private var musicSpeed = 1f
    private var targetSpeed = 1f
    private var sinceSpeedStepMs = 0f
    /** Turned off for good if the device's player refuses tempo changes. */
    private var speedSupported = true

    var sfxEnabled: Boolean = true
    var musicEnabled: Boolean = true
        set(value) {
            field = value
            if (value) resumeMusic() else pauseMusic()
        }

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
    }

    fun loadSfx() {
        if (loaded) return
        loaded = true
        val assets: AssetManager = context.assets
        for (sfx in Sfx.entries) {
            try {
                assets.openFd(sfx.asset).use { fd ->
                    soundIds[sfx] = soundPool.load(fd, 1)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load ${sfx.asset}: ${e.message}")
            }
        }
    }

    /** [rate] is SoundPool's playback rate (0.5-2.0): above 1 plays the effect higher and shorter. */
    fun play(sfx: Sfx, volume: Float = sfx.defaultVolume, rate: Float = 1f) {
        if (!sfxEnabled) return
        val id = soundIds[sfx] ?: return
        val v = volume.coerceIn(0f, 1f)
        soundPool.play(id, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    // ---- Music -----------------------------------------------------------------------------

    fun startMusic() {
        if (music != null) { resumeMusic(); return }
        try {
            val mp = MediaPlayer()
            context.assets.openFd("music/music.mp3").use { fd ->
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            mp.isLooping = true
            mp.setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
            mp.setOnPreparedListener {
                musicPrepared = true
                it.setVolume(musicVolume, musicVolume)
                if (musicEnabled) it.start()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error what=$what extra=$extra"); false
            }
            mp.prepareAsync()
            music = mp
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start music: ${e.message}")
        }
    }

    fun pauseMusic() {
        try { music?.takeIf { it.isPlaying }?.pause() } catch (_: IllegalStateException) {}
    }

    fun resumeMusic() {
        if (!musicEnabled || !musicPrepared) return
        try { music?.takeIf { !it.isPlaying }?.start() } catch (_: IllegalStateException) {}
    }

    /**
     * The mix the game asks for right now. [speed] changes tempo and pitch together, like a tape
     * slowing down (used for slow-mo and the crash wind-down).
     */
    fun setMusicTarget(volume: Float, speed: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
        targetSpeed = speed.coerceIn(0.5f, 1.5f)
    }

    /** Eases the music toward [setMusicTarget]; call once per frame with the frame time. */
    fun tickMusic(dtMs: Float) {
        val mp = music ?: return
        if (!musicPrepared) return
        if (musicVolume != targetVolume) {
            val step = VOLUME_PER_MS * dtMs
            musicVolume = if (musicVolume < targetVolume) min(targetVolume, musicVolume + step)
            else max(targetVolume, musicVolume - step)
            try { mp.setVolume(musicVolume, musicVolume) } catch (_: IllegalStateException) {}
        }
        sinceSpeedStepMs += dtMs
        if (!speedSupported || musicSpeed == targetSpeed || sinceSpeedStepMs < SPEED_STEP_MS) return
        // Setting playback params on a paused player starts it, so only retune while playing.
        val playing = try { mp.isPlaying } catch (_: IllegalStateException) { false }
        if (!playing) return
        // Few, coarse steps: every params change re-buffers, so retuning each frame would crackle.
        val next = if (abs(targetSpeed - musicSpeed) <= SPEED_STEP) targetSpeed
        else musicSpeed + if (targetSpeed > musicSpeed) SPEED_STEP else -SPEED_STEP
        try {
            mp.playbackParams = mp.playbackParams.setSpeed(next).setPitch(next)
            musicSpeed = next
        } catch (e: Exception) {
            Log.w(TAG, "Tempo change not supported: ${e.message}")
            speedSupported = false
        }
        sinceSpeedStepMs = 0f
    }

    fun release() {
        try { music?.release() } catch (_: Exception) {}
        music = null
        musicPrepared = false
        soundPool.release()
    }

    companion object {
        private const val TAG = "NightDriveAudio"
        private const val MUSIC_VOLUME = 0.6f
        private const val VOLUME_PER_MS = 0.0012f   // full swing in under a second
        private const val SPEED_STEP = 0.03f
        private const val SPEED_STEP_MS = 60f
    }
}
