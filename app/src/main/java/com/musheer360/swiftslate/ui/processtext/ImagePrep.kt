package com.musheer360.swiftslate.ui.processtext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.musheer360.swiftslate.api.ImageInput
import java.io.ByteArrayOutputStream

/**
 * Turns a picture the user picked or shared into something small enough to send: turned the
 * right way up, longest side at most [MAX_SIDE] px, re-saved as JPEG, base64-encoded.
 * Phone photos are 4,000+ px and several MB; every provider shrinks them anyway, and sending
 * them full size is slow and can blow past request limits.
 */
object ImagePrep {
    /** Claude and OpenAI both scale down past roughly this, so more pixels buy nothing. */
    const val MAX_SIDE = 1568
    private const val JPEG_QUALITY = 85

    /** Largest power-of-two step down that still leaves the long side at least [maxSide]. */
    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
        var sample = 1
        val longSide = maxOf(width, height)
        while (longSide / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    /** The final size: [width] x [height] shrunk so the long side fits [maxSide], never grown. */
    fun fitWithin(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        val longSide = maxOf(width, height)
        if (longSide <= maxSide || longSide <= 0) return width to height
        val scale = maxSide.toDouble() / longSide
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    /** Reads [uri]. Call off the main thread. Returns null for anything that isn't a readable picture. */
    fun load(context: Context, uri: Uri): ImageInput? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val (w, h) = fitWithin(bitmap.width, bitmap.height)
        if (w != bitmap.width || h != bitmap.height) {
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true).also { if (it !== bitmap) bitmap.recycle() }
        }

        val degrees = rotationOf(context, uri)
        if (degrees != 0) {
            val m = Matrix().apply { postRotate(degrees.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
                .also { if (it !== bitmap) bitmap.recycle() }
        }

        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        bitmap.recycle()
        return ImageInput("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    /** Phone cameras save sideways and note the turn in EXIF; without this the AI sees it sideways. */
    private fun rotationOf(context: Context, uri: Uri): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return 0
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
