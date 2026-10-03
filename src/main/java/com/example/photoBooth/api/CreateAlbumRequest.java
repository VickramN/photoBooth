package com.example.photoBooth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateAlbumRequest {

    @NotBlank
    @Size(max = 255)
    private String albumName;

    @NotBlank
    @Size(max = 255)
    private String cityName;

    @NotBlank
    @Size(max = 255)
    private String countryName;

    public CreateAlbumRequest() {
    }

    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }
}