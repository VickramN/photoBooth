package com.example.photoBooth.api;

import java.util.Map;

public record ValidationErrorResponse(String error, Map<String, String> fields) {

}
