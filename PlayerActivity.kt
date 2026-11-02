package com.nova.anime

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File

class PlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private var libVlc: LibVLC? = null
    private var vlc: MediaPlayer? = null
    private var exo: ExoPlayer? = null
    private var pfd: ParcelFileDescriptor? = null
    private lateinit var surface: SurfaceView
    private lateinit var exoView: PlayerView
    private lateinit var status: TextView
    private lateinit var controls: LinearLayout
    private lateinit var seek: SeekBar
    private lateinit var timeText: TextView
    private lateinit var playBtn: Button
    private var usingExo = false
    private var mediaUri: Uri = Uri.EMPTY
    private var title = ""
    private var attached = false
    private var userSeeking = false
    private var speedIdx = 2
    private var ratioIdx = 0
    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private val ratios = arrayOf("Fit", "16:9", "4:3", "Fill")
    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refresh(); ui.postDelayed(this, 500) }
    }
    private val hideCtl = Runnable { controls.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pathExtra = intent.getStringExtra("path")
        mediaUri = when {
            !pathExtra.isNullOrBlank() -> Uri.fromFile(File(pathExtra))
            intent.data != null -> intent.data!!
            else -> Uri.EMPTY
        }
        title = intent.getStringExtra("title") ?: (mediaUri.lastPathSegment ?: "Video")
        if (mediaUri == Uri.EMPTY) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        buildUi()
        surface.holder.addCallback(this)
        startVlc()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(label: String, onClick: (Button) -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 12f; setTextColor(-1)
        setBackgroundColor(0x00000000); minWidth = 0; minimumWidth = 0
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { onClick(this); showControls() }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        surface = SurfaceView(this)
        exoView = PlayerView(this).apply { useController = false; visibility = View.GONE }
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        root.addView(exoView, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this).apply { text = title; setTextColor(-1); setPadding(dp(14), dp(24), dp(14), dp(8)); setBackgroundColor(0x66000000) }
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0x99000000.toInt()); setPadding(dp(10), dp(6), dp(10), dp(14)) }
        val seekRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        timeText = TextView(this).apply { text = "00:00 / 00:00"; setTextColor(-1); textSize = 12f }
        seek = SeekBar(this).apply { max = 1000 }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) { val d = duration(); if (d > 0) timeText.text = fmt(d * p / 1000) + " / " + fmt(d) }
            }
            override fun onStartTrackingTouch(s: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(s: SeekBar?) {
                val d = duration(); if (d > 0) seekTo(d * (s?.progress ?: 0) / 1000)
                userSeeking = false
            }
        })
        seekRow.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
        seekRow.addView(timeText)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        playBtn = btn("⏸") { togglePlay() }
        row.addView(btn("⏪10") { seekTo(position() - 10000) })
        row.addView(playBtn)
        row.addView(btn("10⏩") { seekTo(position() + 10000) })
        row.addView(btn("1x") { cycleSpeed(it) })
        row.addView(btn("Audio") { pickTrack(true) })
        row.addView(btn("Sub") { pickTrack(false) })
        row.addView(btn("Fit") { cycleRatio(it) })
        controls.addView(seekRow)
        controls.addView(row)
        root.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.setOnClickListener { if (controls.visibility == View.VISIBLE) controls.visibility = View.GONE else showControls() }
        setContentView(root)
        showControls()
    }

    private fun showControls() {
        controls.visibility = View.VISIBLE
        ui.removeCallbacks(hideCtl); ui.postDelayed(hideCtl, 4000)
    }

    // ---------- LibVLC (primary) ----------
    private fun startVlc() {
        try {
            libVlc = LibVLC(this, arrayListOf("--audio-time-stretch", "--network-caching=1500", "--file-caching=1500"))
            vlc = MediaPlayer(libVlc).also { p ->
                p.setEventListener { ev ->
                    if (ev.type == MediaPlayer.Event.EncounteredError) runOnUiThread { startFallback("LibVLC error") }
                }
            }
            if (surface.holder.surface.isValid) attachVlc()
        } catch (e: Throwable) { startFallback("LibVLC unavailable") }
    }

    private fun attachVlc() {
        if (attached || usingExo) return
        try {
            val p = vlc ?: return
            p.vlcVout.setVideoView(surface)
            p.vlcVout.setWindowSize(surface.width.coerceAtLeast(1), surface.height.coerceAtLeast(1))
            p.vlcVout.attachViews()
            attached = true
            val media = if (mediaUri.scheme == "content") {
                pfd = contentResolver.openFileDescriptor(mediaUri, "r")
                Media(libVlc, pfd!!.fileDescriptor)
            } else Media(libVlc, mediaUri)
            media.setHWDecoderEnabled(true, false)
            p.media = media
            media.release()
            p.play()
            ui.post(tick)
        } catch (e: Throwable) { startFallback("LibVLC failed") }
    }

    // ---------- Media3 (fallback) ----------
    private fun startFallback(reason: String) {
        if (usingExo || isFinishing) return
        usingExo = true
        try {
            try { vlc?.stop(); vlc?.vlcVout?.detachViews() } catch (_: Throwable) {}
            surface.visibility = View.GONE
            exoView.visibility = View.VISIBLE
            status.text = "$title · Media3 backup ($reason)"
            exo = ExoPlayer.Builder(this).build().also { p ->
                exoView.player = p
                p.setMediaItem(MediaItem.fromUri(mediaUri))
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        status.text = "এই ফাইলটি কোনো প্লেয়ারে চালানো যায়নি: ${error.errorCodeName}"
                    }
                })
                p.prepare(); p.playWhenReady = true
            }
            ui.post(tick)
        } catch (e: Throwable) { Toast.makeText(this, "দুই Player-ই চালু করা যায়নি: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    // ---------- unified controls ----------
    private fun duration(): Long = if (usingExo) (exo?.duration ?: 0L).coerceAtLeast(0L) else (vlc?.length ?: 0L).coerceAtLeast(0L)
    private fun position(): Long = if (usingExo) (exo?.currentPosition ?: 0L) else (vlc?.time ?: 0L)
    private fun isPlaying(): Boolean = if (usingExo) (exo?.isPlaying ?: false) else (vlc?.isPlaying ?: false)
    private fun seekTo(ms: Long) {
        val t = ms.coerceIn(0L, duration().takeIf { it > 0 } ?: Long.MAX_VALUE)
        if (usingExo) exo?.seekTo(t) else vlc?.time = t
    }
    private fun togglePlay() {
        if (usingExo) { exo?.let { if (it.isPlaying) it.pause() else it.play() } }
        else vlc?.let { if (it.isPlaying) it.pause() else it.play() }
    }
    private fun cycleSpeed(b: Button) {
        speedIdx = (speedIdx + 1) % speeds.size
        val s = speeds[speedIdx]
        if (usingExo) exo?.setPlaybackSpeed(s) else vlc?.rate = s
        b.text = (if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()) + "x"
    }
    private fun cycleRatio(b: Button) {
        ratioIdx = (ratioIdx + 1) % ratios.size
        b.text = ratios[ratioIdx]
        if (usingExo) {
            exoView.resizeMode = if (ratioIdx == 3) 4 else 0 // 4 = ZOOM(fill), 0 = FIT
        } else vlc?.let {
            when (ratioIdx) {
                0 -> { it.setAspectRatio(null); it.setScale(0f) }
                1 -> it.setAspectRatio("16:9")
                2 -> it.setAspectRatio("4:3")
                3 -> { it.setAspectRatio(null); it.setScale(0f) }
            }
        }
    }
    private fun pickTrack(audio: Boolean) {
        if (usingExo) { Toast.makeText(this, "Backup player-এ ট্র্যাক বদল সীমিত", Toast.LENGTH_SHORT).show(); return }
        val p = vlc ?: return
        val tracks = (if (audio) p.audioTracks else p.spuTracks) ?: run {
            Toast.makeText(this, "কোনো ট্র্যাক নেই", Toast.LENGTH_SHORT).show(); return
        }
        if (tracks.isEmpty()) { Toast.makeText(this, "কোনো ট্র্যাক নেই", Toast.LENGTH_SHORT).show(); return }
        val names = tracks.map { it.name ?: "Track ${it.id}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle(if (audio) "Audio track" else "Subtitle").setItems(names) { _, i ->
            if (audio) p.setAudioTrack(tracks[i].id) else p.setSpuTrack(tracks[i].id)
        }.show()
    }
    private fun refresh() {
        playBtn.text = if (isPlaying()) "⏸" else "▶"
        val d = duration()
        if (!userSeeking) {
            timeText.text = fmt(position()) + " / " + fmt(d)
            if (d > 0) seek.progress = (position() * 1000 / d).toInt()
        }
    }
    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    // ---------- surface / lifecycle ----------
    override fun surfaceCreated(holder: SurfaceHolder) { if (!usingExo) attachVlc() }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        try { vlc?.vlcVout?.setWindowSize(width, height) } catch (_: Throwable) {}
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        try { vlc?.vlcVout?.detachViews() } catch (_: Throwable) {}
        attached = false
    }
    override fun onStop() { super.onStop(); try { vlc?.pause(); exo?.pause() } catch (_: Throwable) {} }
    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        try { exo?.release() } catch (_: Throwable) {}
        exo = null
        try { vlc?.stop(); vlc?.vlcVout?.detachViews(); vlc?.release() } catch (_: Throwable) {}
        vlc = null
        try { libVlc?.release() } catch (_: Throwable) {}
        libVlc = null
        try { pfd?.close() } catch (_: Throwable) {}
        super.onDestroy()
    }
}
