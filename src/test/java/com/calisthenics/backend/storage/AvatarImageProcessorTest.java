package com.calisthenics.backend.storage;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarImageProcessorTest {

    private final AvatarImageProcessor processor = new AvatarImageProcessor();

    @Test
    void acceptsAndResizesJpeg() throws Exception {
        byte[] optimized = processor.process(createImage("jpeg", 1200, 800));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(optimized));

        assertTrue(optimized.length <= AvatarImageProcessor.MAX_FILE_SIZE_BYTES);
        assertEquals(512, decoded.getWidth());
        assertEquals(341, decoded.getHeight());
    }

    @Test
    void acceptsPngAndNormalizesItToJpeg() throws Exception {
        byte[] optimized = processor.process(createImage("png", 80, 60));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(optimized));

        assertEquals(80, decoded.getWidth());
        assertEquals(60, decoded.getHeight());
        assertTrue((optimized[0] & 0xFF) == 0xFF && (optimized[1] & 0xFF) == 0xD8);
    }

    @Test
    void rejectsUnsupportedContentEvenWhenItLooksLikeAnImage() {
        assertThrows(ResponseStatusException.class, () -> processor.process("not an image".getBytes()));
    }

    @Test
    void rejectsCorruptedImageContent() {
        assertThrows(ResponseStatusException.class, () -> processor.process(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
    }

    @Test
    void rejectsImagesOverFiveMegabytes() {
        assertThrows(
                ResponseStatusException.class,
                () -> processor.process(new byte[(int) AvatarImageProcessor.MAX_FILE_SIZE_BYTES + 1])
        );
    }

    private byte[] createImage(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        image.flush();
        return output.toByteArray();
    }
}
