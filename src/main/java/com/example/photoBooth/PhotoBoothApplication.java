package com.example.photoBooth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.photoBooth.config.NominatimProperties;

@SpringBootApplication
@EnableConfigurationProperties(NominatimProperties.class)
public class PhotoBoothApplication {

	public static void main(String[] args) {
		SpringApplication.run(PhotoBoothApplication.class, args);
	}

}
