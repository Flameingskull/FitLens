package com.fitlens.companion.video

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.fitlens.companion.data.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Encodes slides into an H.264 MP4 entirely on the phone (no internet, no extra libraries).
 * Frames are drawn with [FrameRenderer], converted to YUV and fed to the hardware encoder.
 */
object VideoExporter {

    private const val FPS = 30
    private const val TIMEOUT_US = 10_000L

    private class Yuv(val y: ByteArray, val u: ByteArray, val v: ByteArray)

    suspend fun export(
        snap: Snapshot,
        slides: List<Slide>,
        opts: SlideOptions,
        out: File,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.Default) {
        val w = opts.width
        val h = opts.height
        val charts = FrameRenderer.chartsFor(snap, slides, opts)
        val holdFrames = maxOf(1, (opts.secondsPerPhoto * FPS).toInt())
        val fadeFrames = if (opts.fade && slides.size > 1) minOf(12, holdFrames / 2) else 0
        // Last slide is held a bit longer so the video doesn't end abruptly.
        val totalFrames = slides.size * holdFrames + FPS

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, maxOf(2_000_000, w * h * 4))
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        out.parentFile?.mkdirs()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()

        fun drain(endOfStream: Boolean) {
            var idle = 0
            while (true) {
                val idx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream || ++idle > 500) return
                    }
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    idx >= 0 -> {
                        val buf = codec.getOutputBuffer(idx)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                        if (buf != null && info.size > 0 && muxerStarted) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(idx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                    }
                }
            }
        }

        fun queueFrame(yuv: Yuv, frameIndex: Int) {
            val pts = frameIndex * 1_000_000L / FPS
            while (true) {
                val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val image = codec.getInputImage(inIdx) ?: throw IllegalStateException("Encoder has no input image")
                    fillImage(image, yuv, w, h)
                    codec.queueInputBuffer(inIdx, 0, w * h * 3 / 2, pts, 0)
                    break
                }
                drain(false)
            }
            drain(false)
        }

        val frameBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val nextBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val blendBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val alphaPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        fun render(target: Bitmap, i: Int) {
            val photo = FrameRenderer.loadBitmap(snap.photoFile(slides[i].photo), w, h)
            FrameRenderer.drawSlide(Canvas(target), photo, slides[i], opts, charts)
            photo?.recycle()
        }

        try {
            var frame = 0
            render(frameBmp, 0)
            for (i in slides.indices) {
                coroutineContext.ensureActive()
                val hasNext = i + 1 < slides.size
                if (hasNext) render(nextBmp, i + 1)
                val steady = toYuv(frameBmp, pixels, w, h)
                val hold = if (hasNext) holdFrames - fadeFrames else holdFrames + FPS
                repeat(hold) { queueFrame(steady, frame++) }
                if (hasNext) {
                    for (f in 1..fadeFrames) {
                        val c = Canvas(blendBmp)
                        c.drawBitmap(frameBmp, 0f, 0f, null)
                        alphaPaint.alpha = (255f * f / (fadeFrames + 1)).toInt()
                        c.drawBitmap(nextBmp, 0f, 0f, alphaPaint)
                        queueFrame(toYuv(blendBmp, pixels, w, h), frame++)
                    }
                    // swap: next becomes current
                    Canvas(frameBmp).drawBitmap(nextBmp, 0f, 0f, null)
                }
                onProgress(frame.toFloat() / totalFrames)
            }
            // End of stream
            while (true) {
                val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    codec.queueInputBuffer(inIdx, 0, 0, frame * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                }
                drain(false)
            }
            drain(true)
        } finally {
            try { codec.stop() } catch (e: Exception) {}
            codec.release()
            try { if (muxerStarted) muxer.stop() } catch (e: Exception) {}
            muxer.release()
            frameBmp.recycle(); nextBmp.recycle(); blendBmp.recycle()
        }
        onProgress(1f)
        out
    }

    private fun toYuv(bmp: Bitmap, px: IntArray, w: Int, h: Int): Yuv {
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val y = ByteArray(w * h)
        val cw = w / 2
        val ch = h / 2
        val u = ByteArray(cw * ch)
        val v = ByteArray(cw * ch)
        for (j in 0 until h) {
            val row = j * w
            for (i in 0 until w) {
                val c = px[row + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                y[row + i] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte()
                if ((j and 1) == 0 && (i and 1) == 0) {
                    val ci = (j / 2) * cw + (i / 2)
                    u[ci] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte()
                    v[ci] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte()
                }
            }
        }
        return Yuv(y, u, v)
    }

    private fun fillImage(image: Image, yuv: Yuv, w: Int, h: Int) {
        val planes = image.planes
        // Y plane
        val yp = planes[0]
        val yb = yp.buffer
        val yRow = yp.rowStride
        val yPix = yp.pixelStride
        if (yPix == 1) {
            for (j in 0 until h) {
                yb.position(j * yRow)
                yb.put(yuv.y, j * w, w)
            }
        } else {
            for (j in 0 until h) for (i in 0 until w) yb.put(j * yRow + i * yPix, yuv.y[j * w + i])
        }
        // Chroma planes (handles planar I420 and semi-planar NV12/NV21 layouts)
        val cw = w / 2
        val ch = h / 2
        val up = planes[1]
        val vp = planes[2]
        val ub = up.buffer
        val vb = vp.buffer
        if (up.pixelStride == 1 && vp.pixelStride == 1) {
            for (j in 0 until ch) {
                ub.position(j * up.rowStride); ub.put(yuv.u, j * cw, cw)
                vb.position(j * vp.rowStride); vb.put(yuv.v, j * cw, cw)
            }
        } else {
            val ur = up.rowStride
            val us = up.pixelStride
            val vr = vp.rowStride
            val vs = vp.pixelStride
            for (j in 0 until ch) {
                val src = j * cw
                val uo = j * ur
                val vo = j * vr
                for (i in 0 until cw) {
                    ub.put(uo + i * us, yuv.u[src + i])
                    vb.put(vo + i * vs, yuv.v[src + i])
                }
            }
        }
    }

    /** Copies a finished video into the phone's Movies/FitLens folder so it shows in the gallery. */
    fun saveToGallery(context: Context, file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/FitLens")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { os -> file.inputStream().use { it.copyTo(os) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    fun saveImageToGallery(context: Context, bmp: Bitmap, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FitLens")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return uri
    }
}
