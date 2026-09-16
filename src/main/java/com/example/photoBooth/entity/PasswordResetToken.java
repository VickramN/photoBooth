package com.example.photoBooth.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
public class PasswordResetToken {

    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne 
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;


    public PasswordResetToken(){

    }

    public PasswordResetToken(User user, String tokenHash, Instant expiresAt){
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getId(){
        return id;
    }

    public User getUser(){
        return user;
    }

    public String getTokenHash(){
        return tokenHash;
    }

    public Instant getExpiresAt(){
        return expiresAt;
    }
    
}
