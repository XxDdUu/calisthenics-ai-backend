package com.calisthenics.backend.storage;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Component
public class AvatarImageProcessor {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DIMENSION = 512;
    private static final long MAX_PIXEL_COUNT = 40_000_000L;

    public byte[] process(byte[] input) {
        if (input == null || input.length == 0) {
            throw invalidImage("The selected image is empty.");
        }
        if (input.length > MAX_FILE_SIZE_BYTES) {
            throw invalidImage("Avatar images must be 5 MB or smaller.");
        }

        String detectedFormat = detectFormat(input);
        if (detectedFormat == null) {
            throw invalidImage("Only JPEG, PNG, and WebP images are supported.");
        }

        BufferedImage image = decode(input, detectedFormat);
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXEL_COUNT) {
            throw invalidImage("The image dimensions are not supported.");
        }

        double scale = Math.min(1d, (double) MAX_DIMENSION / Math.max(width, height));
        int outputWidth = Math.max(1, (int) Math.round(width * scale));
        int outputHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage optimized = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = optimized.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, outputWidth, outputHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, outputWidth, outputHeight, null);
        } finally {
            graphics.dispose();
            image.flush();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(optimized, "jpeg", output)) {
                throw new IllegalStateException("JPEG image encoder is unavailable.");
            }
            optimized.flush();
            byte[] result = output.toByteArray();
            if (result.length > MAX_FILE_SIZE_BYTES) {
                throw invalidImage("The optimized avatar exceeds the 5 MB limit.");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not optimize the avatar image.", exception);
        }
    }

    private BufferedImage decode(byte[] input, String expectedFormat) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            if (imageInput == null) {
                throw invalidImage("The selected file is not a valid image.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalidImage("The selected file is not a valid or supported image.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String actualFormat = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                boolean matches = expectedFormat.equals(actualFormat) ||
                        (expectedFormat.equals("jpeg") && actualFormat.equals("jpg"));
                if (!matches) {
                    throw invalidImage("The image content does not match its format.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXEL_COUNT) {
                    throw invalidImage("The image dimensions are not supported.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw invalidImage("The selected file is corrupted or unsupported.");
                }
                return decoded;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalidImage("The selected file is corrupted or unsupported.");
        }
    }

    private String detectFormat(byte[] input) {
        if (input.length >= 3 && (input[0] & 0xFF) == 0xFF &&
                (input[1] & 0xFF) == 0xD8 && (input[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (input.length >= 8 && (input[0] & 0xFF) == 0x89 &&
                input[1] == 'P' && input[2] == 'N' && input[3] == 'G' &&
                input[4] == 0x0D && input[5] == 0x0A && input[6] == 0x1A && input[7] == 0x0A) {
            return "png";
        }
        if (input.length >= 12 && input[0] == 'R' && input[1] == 'I' &&
                input[2] == 'F' && input[3] == 'F' && input[8] == 'W' &&
                input[9] == 'E' && input[10] == 'B' && input[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private ResponseStatusException invalidImage(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
