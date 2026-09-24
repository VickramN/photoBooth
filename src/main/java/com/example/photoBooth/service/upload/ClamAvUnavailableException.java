package com.example.photoBooth.service.upload;

public class ClamAvUnavailableException extends RuntimeException {

    public ClamAvUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClamAvUnavailableException(String message) {
        super(message);
    }
}