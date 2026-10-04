package com.example.photoBooth.api;

import java.util.Map;

public record ValidationErrorResponse(ErrorCode error, Map<String, String> fields) {

}
