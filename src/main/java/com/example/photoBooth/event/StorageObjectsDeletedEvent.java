package com.example.photoBooth.event;

import java.util.List;

public record StorageObjectsDeletedEvent(List<String> objectKeys) {

} 
