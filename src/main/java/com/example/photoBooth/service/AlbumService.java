package com.example.photoBooth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.photoBooth.entity.Album;
import com.example.photoBooth.entity.Image;
import com.example.photoBooth.entity.User;
import com.example.photoBooth.event.StorageObjectsDeletedEvent;
import com.example.photoBooth.repository.AlbumRepository;
import com.example.photoBooth.repository.UserRepository;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AlbumService {

    private static final Logger logger = LoggerFactory.getLogger(AlbumService.class);

    private final ApplicationEventPublisher eventPublisher;

    private final AlbumRepository albumRepository;
    private final UserRepository userRepository;
    private final GeocodingService geocodingService;

    public AlbumService(AlbumRepository albumRepository, UserRepository userRepository,
            GeocodingService geocodingService, ApplicationEventPublisher eventPublisher) {
        this.albumRepository = albumRepository;
        this.userRepository = userRepository;
        this.geocodingService = geocodingService;
        this.eventPublisher = eventPublisher;
    }

    public List<Album> findAll(UUID ownerId) {
        logger.info("Fetching all albums for owner {}", ownerId);
        return albumRepository.findByOwner_Id(ownerId);
    }

    public List<Album> findByCityName(String cityName, UUID ownerId) {
        logger.info("Fetching albums by city {} for owner {}", cityName, ownerId);
        return albumRepository.findByOwner_IdAndCityNameIgnoreCase(ownerId, cityName);
    }

    public List<Album> findByCountryName(String countryName, UUID ownerId) {
        logger.info("Fetching albums by country {} for owner {}", countryName, ownerId);
        return albumRepository.findByOwner_IdAndCountryNameIgnoreCase(ownerId, countryName);
    }

    public List<Album> findByCityNameAndCountryName(String cityName, String countryName, UUID ownerId) {
        logger.info("Fetching albums by city {} and country {} for owner {}", cityName, countryName, ownerId);
        return albumRepository.findByOwner_IdAndCityNameIgnoreCaseAndCountryNameIgnoreCase(ownerId, cityName,
                countryName);
    }

    public Optional<Album> findById(UUID id, UUID ownerId) {
        logger.info("Searching for album with id {} for owner {}", id, ownerId);

        Optional<Album> album = albumRepository.findByIdAndOwner_Id(id, ownerId);

        if (album.isPresent()) {
            logger.info("Album found with id {}", id);
        } else {
            logger.warn("Album not found (or not owned) with id {}", id);
        }

        return album;
    }

    public Album create(Album album, UUID ownerId) {
        logger.info("Creating album with name {} for owner {}", album.getAlbumName(), ownerId);

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + ownerId));
        album.setOwner(owner);

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

    @Transactional
    public boolean deleteById(UUID id, UUID ownerId) {
        logger.info("Deleting album with id {} for owner {}", id, ownerId);

        Optional<Album> optionalAlbum = findById(id, ownerId);

        if (optionalAlbum.isEmpty()) {
            return false;
        }

        List<String> objectKeys = optionalAlbum.get().getImages().stream()
                .map(Image::getObjectKey)
                .toList();

        albumRepository.deleteById(id);
        eventPublisher.publishEvent(new StorageObjectsDeletedEvent(objectKeys));
        logger.info("Album deleted with id {}", id);
        return true;
    }
}