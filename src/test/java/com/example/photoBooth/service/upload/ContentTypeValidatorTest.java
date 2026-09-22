package com.example.photoBooth.service.upload;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentTypeValidatorTest {

    private final ContentTypeValidator validator = new ContentTypeValidator();

    @Test
    void shouldAcceptGenuineJpeg() throws IOException {
        assertTrue(validator.isAllowedImage(genuineJpegBytes()));
    }

    @Test
    void shouldRejectTextFileDisguisedAsImage() {
        byte[] bytes = "this is not an image".getBytes();
        assertFalse(validator.isAllowedImage(bytes));
    }

    @Test
    void shouldRejectExecutableDisguisedAsImage() {
        byte[] windowsExeHeader = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00};
        assertFalse(validator.isAllowedImage(windowsExeHeader));
    }

    private byte[] genuineJpegBytes() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}