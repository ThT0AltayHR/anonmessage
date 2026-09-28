package com.anonymous.app.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.anonymous.app.R

/** Videonun uzunlugu 8.0 sn. Son kare tamamen siyah oldugu icin dogrudan uygulamaya gecilir. */
const val INTRO_MS = 8000L

/**
 * Tam ekran, bosluksuz (center-crop) video.
 * VideoView orantiyi koruyup siyah kenar biraktigi icin TextureView + kendi matrisimizle olcekleriz.
 * Cokme guvenligi: her MediaPlayer hatasinda, cihaz codec'i desteklemediginde bile onFinished cagrilir.
 */
@Composable
fun IntroVideo(onFinished: () -> Unit) {
    val ctx = LocalContext.current
    var done by remember { mutableStateOf(false) }
    val finish = remember { { if (!done) { done = true; onFinished() } } }
    val player = remember { MediaPlayer() }

    // Emniyet: video hicbir sebeple bitmezse 9 sn sonra yine de gec
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(INTRO_MS + 1000); finish() }

    DisposableEffect(Unit) {
        onDispose { try { player.reset(); player.release() } catch (_: Exception) {} }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                TextureView(c).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            try {
                                val afd = ctx.resources.openRawResourceFd(R.raw.intro)
                                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                afd.close()
                                player.setSurface(Surface(st))
                                player.isLooping = false
                                player.setOnVideoSizeChangedListener { _, vw, vh -> applyCenterCrop(this@apply, vw, vh) }
                                player.setOnCompletionListener { finish() }
                                player.setOnErrorListener { _, _, _ -> finish(); true }
                                player.setOnPreparedListener { it.start() }
                                player.prepareAsync()
                            } catch (e: Exception) { finish() }
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                            try { applyCenterCrop(this@apply, player.videoWidth, player.videoHeight) } catch (_: Exception) {}
                        }
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
        )
    }
}

/** Videoyu ekrani tamamen dolduracak sekilde buyutup ortalar (kenarlardan kirpar). */
private fun applyCenterCrop(view: TextureView, videoW: Int, videoH: Int) {
    if (videoW <= 0 || videoH <= 0) return
    val vw = view.width.toFloat(); val vh = view.height.toFloat()
    if (vw <= 0f || vh <= 0f) return
    // TextureView icerigi ilk basta view boyutuna gerilmis olarak gelir; once oranini duzeltir, sonra kaplatiriz
    val scale = maxOf(vw / videoW, vh / videoH)
    val sx = (videoW * scale) / vw
    val sy = (videoH * scale) / vh
    val m = Matrix()
    m.setScale(sx, sy, vw / 2f, vh / 2f)
    view.setTransform(m)
}
