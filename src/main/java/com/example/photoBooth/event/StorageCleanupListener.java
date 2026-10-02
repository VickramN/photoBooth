package com.example.photoBooth.event;

import com.example.photoBooth.service.ImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Component 
public class StorageCleanupListener {
    
    private static final Logger logger = LoggerFactory.getLogger(StorageCleanupListener.class);
    
    private final ImageStorageService imageStorageService;

    public StorageCleanupListener(ImageStorageService imageStorageService){
        this.imageStorageService = imageStorageService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStorageObjectsDeleted(StorageObjectsDeletedEvent event){
        for (String key : event.objectKeys()){
            try {
                imageStorageService.delete(key);
            } catch (Exception e) {
                logger.warn("Failed to delte R2 Object {} after commit, it is now orphaned", key, e);
            }
        }
    }

}
