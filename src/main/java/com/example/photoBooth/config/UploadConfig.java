package com.example.photoBooth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.SocketFactory;

@Configuration
public class UploadConfig {

    @Bean
    public SocketFactory socketFactory() {
        return SocketFactory.getDefault();
    }
}