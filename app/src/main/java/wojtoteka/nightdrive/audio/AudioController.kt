package wojtoteka.nightdrive.audio

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log

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
 */
class AudioController(private val context: Context) {

    private val soundPool: SoundPool
    private val soundIds = HashMap<Sfx, Int>()
    private var loaded = false

    private var music: MediaPlayer? = null
    private var musicPrepared = false

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

    fun play(sfx: Sfx, volume: Float = sfx.defaultVolume) {
        if (!sfxEnabled) return
        val id = soundIds[sfx] ?: return
        val v = volume.coerceIn(0f, 1f)
        soundPool.play(id, v, v, 1, 0, 1f)
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

    fun release() {
        try { music?.release() } catch (_: Exception) {}
        music = null
        musicPrepared = false
        soundPool.release()
    }

    companion object {
        private const val TAG = "NightDriveAudio"
        private const val MUSIC_VOLUME = 0.6f
    }
}
