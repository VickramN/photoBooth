package com.example.photoBooth.service.upload;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class ImageReencoder {

    public byte[] reencode(byte[] originalBytes, int maxDimensionPx) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (original == null) {
            throw new IOException("Unable to decode image bytes");
        }

        int longestSide = Math.max(original.getWidth(), original.getHeight());
        double scale = longestSide > maxDimensionPx ? (double) maxDimensionPx / longestSide : 1.0;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(original)
                .scale(scale)
                .outputFormat("jpg")
                .outputQuality(0.9)
                .toOutputStream(out);
        return out.toByteArray();
    }
}