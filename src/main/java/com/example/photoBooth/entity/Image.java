package com.example.photoBooth.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String img;

    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "album_id")
    private Album album;

    public Image() {
    }

    public Image(int id, String img, Album album) {
        this.id = id;
        this.img = img;
        this.album = album;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
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

    public Integer getAlbumId() {
        return (album != null) ? album.getId() : null;
    }
}
