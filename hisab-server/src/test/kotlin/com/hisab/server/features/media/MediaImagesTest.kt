package com.hisab.server.features.media

import com.hisab.server.plugins.RequestFailure
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class MediaImagesTest {
    private fun png(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try { graphics.color = Color.BLUE; graphics.fillRect(0, 0, width, height) } finally { graphics.dispose() }
        return ByteArrayOutputStream().use { output ->
            try { ImageIO.write(image, "png", output); output.toByteArray() } finally { image.flush() }
        }
    }
    @Test fun normalizesPortraitIntoBoundedSquareJpeg() {
        val result = normalizedImage(png(600, 1000))
        assertTrue(result.size < 512 * 1024)
        val decoded = ImageIO.read(ByteArrayInputStream(result))
        try {
            assertEquals(512, decoded.width); assertEquals(512, decoded.height)
            assertTrue(Color(decoded.getRGB(200, 200)).blue > 200)
            assertEquals(0xff.toByte(), result[0]); assertEquals(0xd8.toByte(), result[1])
        } finally { decoded.flush() }
    }
    @Test fun rejectsCorruptHeadersAndExcessiveDimensions() {
        for (bytes in listOf(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47), png(8193, 1), "not a picture".toByteArray())) {
            assertTrue(runCatching { normalizedImage(bytes) }.exceptionOrNull() is RequestFailure)
        }
    }
    @Test fun rejectsOversizedPayloadBeforeParsing() {
        assertTrue(runCatching { normalizedImage(ByteArray(MAX_IMAGE_INPUT + 1)) }.exceptionOrNull() is RequestFailure)
    }
}
