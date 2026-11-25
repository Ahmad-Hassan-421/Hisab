package com.hisab.server.features.media

import com.hisab.server.plugins.RequestFailure
import io.ktor.http.HttpStatusCode
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

internal const val MAX_IMAGE_INPUT = 5 * 1024 * 1024
internal val mediaIdPattern = Regex("^[a-f0-9-]{36}$")
internal fun mediaPath(id: String) = "/media/$id"
internal fun mediaId(path: String): String? = path.removePrefix("/media/").takeIf {
    path.startsWith("/media/") && mediaIdPattern.matches(it)
}

/** Inspect dimensions before decoding, subsample, crop, and re-encode without source metadata. */
internal fun normalizedImage(bytes: ByteArray): ByteArray {
    if (bytes.size > MAX_IMAGE_INPUT) throw RequestFailure(HttpStatusCode.PayloadTooLarge, "Choose an image smaller than 5 MB")
    try {
        MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) throw IllegalArgumentException()
            val reader = readers.next()
            try {
                check(reader.formatName.lowercase() in setOf("jpeg", "jpg", "png"))
                reader.setInput(input, true, true)
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                check(width in 1..8192 && height in 1..8192 && width.toLong() * height <= 20_000_000)
                val params = reader.defaultReadParam
                val step = (minOf(width, height) / 512).coerceAtLeast(1)
                params.setSourceSubsampling(step, step, 0, 0)
                val decoded = reader.read(0, params) ?: throw IllegalArgumentException()
                try {
                    val side = minOf(decoded.width, decoded.height)
                    val size = minOf(side, 512)
                    val result = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
                    val graphics = result.createGraphics()
                    try {
                        graphics.color = Color.WHITE
                        graphics.fillRect(0, 0, size, size)
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                        val x = (decoded.width - side) / 2
                        val y = (decoded.height - side) / 2
                        graphics.drawImage(decoded, 0, 0, size, size, x, y, x + side, y + side, null)
                    } finally { graphics.dispose() }
                    return ByteArrayOutputStream().use { output ->
                        try { check(ImageIO.write(result, "jpeg", output)) } finally { result.flush() }
                        output.toByteArray().also { check(it.size <= 512 * 1024) }
                    }
                } finally { decoded.flush() }
            } finally { reader.dispose() }
        }
    } catch (failure: RequestFailure) { throw failure }
    catch (_: Exception) { throw RequestFailure(HttpStatusCode.UnsupportedMediaType, "Choose a valid PNG or JPEG image") }
}
