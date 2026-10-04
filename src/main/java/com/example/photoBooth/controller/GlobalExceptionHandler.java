package com.example.photoBooth.controller;

import com.example.photoBooth.api.ErrorResponse;
import com.example.photoBooth.api.ValidationErrorResponse;
import com.example.photoBooth.service.SelfModificationException;
import com.example.photoBooth.service.upload.ClamAvUnavailableException;
import com.example.photoBooth.service.upload.UploadReadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;

import java.util.Map;
import java.util.LinkedHashMap;

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

    @ExceptionHandler(SelfModificationException.class)
    public ResponseEntity<ErrorResponse> handleSelfModification(SelfModificationException e) {
        logger.warn("Admin action refused: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("CANNOT_MODIFY_SELF"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        logger.warn("Malformed request body: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse("MALFORMED_REQUEST"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        logger.warn("Invalid value for parameter '{}': {}", e.getName(), e.getValue());
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_PARAMETER"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity.badRequest().body(new ValidationErrorResponse("VALIDATION_FAILED", fields));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("FORBIDDEN"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        logger.error("Unhandled Exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR"));
    }
}
