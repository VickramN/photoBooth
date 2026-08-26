package com.example.photoBooth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.photoBooth.entity.Album;
import com.example.photoBooth.repository.AlbumRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AlbumService {

    private static final Logger logger = LoggerFactory.getLogger(AlbumService.class);

    private final AlbumRepository albumRepository;
    private final GeocodingService geocodingService;

    public AlbumService(AlbumRepository albumRepository, GeocodingService geocodingService) {
        this.albumRepository = albumRepository;
        this.geocodingService = geocodingService;
    }

    public List<Album> findAll() {
        logger.info("Fetching all albums");
        return albumRepository.findAll();
    }

    public List<Album> findByCityName(String cityName) {
        logger.info("Fetching albums by city {}", cityName);
        return albumRepository.findByCityName(cityName);
    }

    public List<Album> findByCityNameAndCountryName(String cityName, String countryName) {
        logger.info("Fetching albums by city {} and country {}", cityName, countryName);
        return albumRepository.findByCityNameAndCountryName(cityName, countryName);
    }

    public Optional<Album> findById(UUID id) {
        logger.info("Searching for album with id {}", id);

        Optional<Album> album = albumRepository.findById(id);

        if (album.isPresent()) {
            logger.info("Album found with id {}", id);
        } else {
            logger.warn("Album not found with id {}", id);
        }

        return album;
    }

    public Album create(Album album) {
        logger.info("Creating album with name {}", album.getAlbumName());

        Optional<GeocodingService.Coordinates> coordinates = geocodingService.geocode(album.getCityName(),
                album.getCountryName());

        if (coordinates.isPresent()) {
            album.setLat(coordinates.get().lat());
            album.setLang(coordinates.get().lang());
        } else {
            logger.warn("Geocoding returned no result for album with city {} and country {}", album.getCityName(),
                    album.getCountryName());
        }
        Album savedAlbum = albumRepository.save(album);

        logger.info("Album saved successfully with id {}", savedAlbum.getId());

        return savedAlbum;
    }

    public void deleteById(UUID id) {
        logger.info("Deleting album with id {}", id);

        albumRepository.deleteById(id);

        logger.info("Album deleted with id {}", id);
    }
}