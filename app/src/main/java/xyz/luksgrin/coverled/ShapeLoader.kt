package xyz.luksgrin.coverled

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.FileOutputStream

/**
 * Imports and serves the user's custom indicator shape.
 *
 * Accepted input: a PNG (with alpha) up to [Settings.SHAPE_MAX_INPUT_PX] per side and
 * [Settings.SHAPE_MAX_INPUT_BYTES]. The drawing should be white (grayscale = dimmer) on a
 * transparent background; it is tinted with each app's color at render time. Stored downscaled
 * to [Settings.SHAPE_STORED_PX]² in the app's private files.
 */
object ShapeLoader {
    private const val TAG = "CoverLED"
    @Volatile private var cache: Bitmap? = null

    fun load(context: Context): Bitmap? {
        cache?.let { return it }
        val f = Settings.shapeFile(context)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path)?.also { cache = it }
    }

    /** @return error message, or null on success. */
    fun import(context: Context, uri: Uri): String? {
        val cr = context.contentResolver
        val size = runCatching { cr.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
        if (size > Settings.SHAPE_MAX_INPUT_BYTES) return "File is larger than 2 MB"

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return "Cannot open file"
        if (bounds.outMimeType != "image/png") return "Not a PNG (${bounds.outMimeType ?: "unknown type"})"
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return "Cannot decode image"
        if (bounds.outWidth > Settings.SHAPE_MAX_INPUT_PX || bounds.outHeight > Settings.SHAPE_MAX_INPUT_PX)
            return "Image larger than ${Settings.SHAPE_MAX_INPUT_PX}×${Settings.SHAPE_MAX_INPUT_PX}"

        val src = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return "Cannot decode image"
        if (!src.hasAlpha()) { src.recycle(); return "PNG has no transparency; the background must be transparent" }

        // fit into a square of SHAPE_STORED_PX, preserving aspect, centered
        val px = Settings.SHAPE_STORED_PX
        val scale = px.toFloat() / maxOf(src.width, src.height)
        val w = (src.width * scale).toInt().coerceAtLeast(1); val h = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).drawBitmap(scaled, ((px - w) / 2).toFloat(), ((px - h) / 2).toFloat(), null)
        if (scaled !== src) scaled.recycle(); src.recycle()

        // must actually contain something visible
        val pixels = IntArray(px * px); out.getPixels(pixels, 0, px, 0, 0, px, px)
        if (pixels.none { (it ushr 24) > 16 }) { out.recycle(); return "Image is fully transparent" }

        FileOutputStream(Settings.shapeFile(context)).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cache = out
        Log.i(TAG, "custom shape imported (${bounds.outWidth}x${bounds.outHeight} -> ${px}x$px)")
        return null
    }

    fun clear(context: Context) { Settings.shapeFile(context).delete(); cache = null }
}
