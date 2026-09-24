package com.example.photoBooth.service;

import com.example.photoBooth.entity.Image;

public sealed interface ImageUploadResult {

    record Success(Image image) implements ImageUploadResult {
    }

    record Failure(UploadError error) implements ImageUploadResult {
    }
}
