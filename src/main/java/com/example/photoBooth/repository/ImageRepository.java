package com.example.photoBooth.repository;

import com.example.photoBooth.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageRepository extends JpaRepository<Image, Integer> {

    List<Image> findByAlbum_Id(int albumId);

    void deleteByAlbum_IdAndId(int albumId, int id);
}