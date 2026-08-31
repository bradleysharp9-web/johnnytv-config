package com.johnnytv.player

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var statusLabel: TextView

    private var player: ExoPlayer? = null
    private var urls: List<String> = emptyList()
    private var urlIndex = 0
    private var title: String = ""
    private var isLive: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        statusLabel = findViewById(R.id.playerStatus)

        urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: emptyList()
        title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        isLive = intent.getBooleanExtra(EXTRA_LIVE, true)

        if (urls.isEmpty()) {
            showStatus("Nothing to play.")
            return
        }

        playerView.keepScreenOn = true
        playerView.setShowNextButton(false)
        playerView.setShowPreviousButton(false)
        playerView.setControllerShowTimeoutMs(4000)
    }

    override fun onStart() {
        super.onStart()
        if (urls.isNotEmpty()) startPlayback()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun startPlayback() {
        releasePlayer()
        if (urlIndex >= urls.size) {
            showStatus("This stream would not play.\nThe server may be busy or the channel offline.")
            return
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Config.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()

        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Live streams are served as .m3u8 by some panels and .ts by others.
                // Fall through the candidate URLs before giving up.
                urlIndex++
                if (urlIndex < urls.size) {
                    showStatus(getString(R.string.loading))
                    playerView.post { startPlayback() }
                } else {
                    showStatus(
                        "Could not play \"$title\".\n" +
                            (error.message ?: "Playback error")
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> showStatus(getString(R.string.loading))
                    Player.STATE_READY -> hideStatus()
                    Player.STATE_ENDED -> if (!isLive) finish()
                    else -> Unit
                }
            }
        })

        playerView.player = exo
        exo.setMediaItem(MediaItem.fromUri(urls[urlIndex]))
        exo.prepare()
        exo.playWhenReady = true
        player = exo

        showStatus(getString(R.string.loading))
    }

    private fun releasePlayer() {
        player?.let {
            it.stop()
            it.release()
        }
        player = null
        playerView.player = null
    }

    private fun showStatus(message: String) {
        statusLabel.text = message
        statusLabel.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        statusLabel.visibility = View.GONE
    }

    companion object {
        const val EXTRA_URLS = "extra_urls"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_LIVE = "extra_live"
    }
}
