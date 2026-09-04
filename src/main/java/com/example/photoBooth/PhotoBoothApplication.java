package com.example.photoBooth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.example.photoBooth.config.NominatimProperties;
import com.example.photoBooth.config.R2Properties;
import com.example.photoBooth.security.JwtProperties;

@SpringBootApplication
@EnableConfigurationProperties({ NominatimProperties.class, R2Properties.class, JwtProperties.class })
public class PhotoBoothApplication {

	public static void main(String[] args) {
		SpringApplication.run(PhotoBoothApplication.class, args);
	}

}
