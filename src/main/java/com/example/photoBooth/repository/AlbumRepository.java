package com.example.photoBooth.repository;

import com.example.photoBooth.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlbumRepository extends JpaRepository<Album, UUID> {

    List<Album> findByOwner_Id(UUID ownerId);

    List<Album> findByOwner_IdAndCityName(UUID ownerId, String cityName);

    List<Album> findByOwner_IdAndCityNameAndCountryName(UUID ownerId, String cityName, String countryName);
}