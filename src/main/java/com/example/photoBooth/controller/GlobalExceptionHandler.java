package com.example.photoBooth.controller;

import com.example.photoBooth.api.ErrorResponse;
import com.example.photoBooth.service.upload.ClamAvUnavailableException;
import com.example.photoBooth.service.upload.UploadReadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        logger.warn("Upload rejected, exceeded container max upload size: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse("FILE_TOO_LARGE"));
    }

    @ExceptionHandler(ClamAvUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleClamAvUnavailable(ClamAvUnavailableException e) {
        logger.error("Upload rejected, AV scanner unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse("SCAN_UNAVAILABLE"));
    }

    @ExceptionHandler(UploadReadException.class)
    public ResponseEntity<ErrorResponse> handleUploadReadFailure(UploadReadException e) {
        logger.error("Upload rejected, failed to read uploaded file: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("UPLOAD_READ_FAILED"));
    }
}
