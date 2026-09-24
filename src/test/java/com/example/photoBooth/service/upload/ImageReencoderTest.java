package com.example.photoBooth.service.upload;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageReencoderTest {

    private final ImageReencoder reencoder = new ImageReencoder();

    @Test
    void shouldCapDimensionsToMax() throws IOException {
        byte[] original = buildJpeg(500, 300);

        byte[] result = reencoder.reencode(original, 100);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(100, Math.max(decoded.getWidth(), decoded.getHeight()));
    }

    @Test
    void shouldNotUpscaleImagesSmallerThanMax() throws IOException {
        byte[] original = buildJpeg(50, 30);

        byte[] result = reencoder.reencode(original, 4096);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(50, decoded.getWidth());
        assertEquals(30, decoded.getHeight());
    }

    @Test
    void shouldAlwaysOutputJpegRegardlessOfInputFormat() throws IOException {
        byte[] originalPng = buildPng(40, 40);

        byte[] result = reencoder.reencode(originalPng, 4096);

        assertEquals("JPEG", detectFormat(result));
    }

    private byte[] buildJpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private byte[] buildPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String detectFormat(byte[] bytes) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(iis);
            assertTrue(readers.hasNext());
            return readers.next().getFormatName().toUpperCase();
        }
    }
}