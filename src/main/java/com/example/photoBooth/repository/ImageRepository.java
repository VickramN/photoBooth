package com.example.photoBooth.repository;

import com.example.photoBooth.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImageRepository extends JpaRepository<Image, UUID> {

    List<Image> findByAlbum_Id(UUID albumId);

    void deleteByAlbum_IdAndId(UUID albumId, UUID id);
}