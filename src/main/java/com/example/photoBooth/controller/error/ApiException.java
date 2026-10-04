package com.example.photoBooth.controller.error;

import com.example.photoBooth.api.ErrorCode;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public ApiException(HttpStatus status, ErrorCode code) {
        super(code.name());
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }
}