package com.example.photoBooth.entity;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String img;

    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "album_id")
    private Album album;

    public Image() {
    }

    public Image(UUID id, String img, Album album) {
        this.id = id;
        this.img = img;
        this.album = album;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getImg() {
        return img;
    }

    public void setImg(String img) {
        this.img = img;
    }

    public Album getAlbum() {
        return album;
    }

    public void setAlbum(Album album) {
        this.album = album;
    }

    public UUID getAlbumId() {
        return (album != null) ? album.getId() : null;
    }
}
