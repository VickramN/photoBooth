package com.example.photoBooth.repository;

import com.example.photoBooth.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface AlbumRepository extends JpaRepository<Album, UUID> {

    List<Album> findByOwner_Id(UUID ownerId);

    List<Album> findByOwner_IdAndCityNameIgnoreCase(UUID ownerId, String cityName);

    List<Album> findByOwner_IdAndCountryNameIgnoreCase(UUID ownerId, String countryName);

    List<Album> findByOwner_IdAndCityNameIgnoreCaseAndCountryNameIgnoreCase(UUID ownerId, String cityName,
            String countryName);

    boolean existsByOwner_Id(UUID ownerId);

    boolean existsByIdAndOwner_Id(UUID id, UUID ownerId);

    Optional<Album> findByIdAndOwner_Id(UUID id, UUID ownerId);
}