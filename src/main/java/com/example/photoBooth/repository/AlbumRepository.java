package com.example.photoBooth.repository;

import com.example.photoBooth.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlbumRepository extends JpaRepository<Album, Integer> {

    List<Album> findByCityName(String cityName);

    List<Album> findByCityNameAndCountryName(String cityName, String countryName);
}