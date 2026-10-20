package com.shield.app

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Classifier private constructor(private val interp: Interpreter) {
    private val input = ByteBuffer.allocateDirect(224 * 224 * 3 * 4).order(ByteOrder.nativeOrder())
    private val out = Array(1) { FloatArray(5) }
    private val px = IntArray(224 * 224)

    /** Returns porn + hentai probability (0..1). */
    fun score(b: Bitmap): Float = try {
        val s = if (b.width == 224 && b.height == 224) b else Bitmap.createScaledBitmap(b, 224, 224, true)
        s.getPixels(px, 0, 224, 0, 0, 224, 224)
        input.rewind()
        for (p in px) {
            input.putFloat(((p shr 16) and 255) / 255f)
            input.putFloat(((p shr 8) and 255) / 255f)
            input.putFloat((p and 255) / 255f)
        }
        input.rewind()
        interp.run(input, out)
        out[0][1] + out[0][3]
    } catch (e: Exception) { 0f }

    companion object {
        const val FILE = "nsfw.tflite"
        fun available(c: Context): Boolean = try { c.assets.list("")?.contains(FILE) == true } catch (e: Exception) { false }
        fun load(c: Context): Classifier? = try {
            val bytes = c.assets.open(FILE).use { it.readBytes() }
            val bb = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            bb.put(bytes); bb.rewind()
            Classifier(Interpreter(bb, Interpreter.Options().setNumThreads(1)))
        } catch (e: Throwable) { null }
    }
}
