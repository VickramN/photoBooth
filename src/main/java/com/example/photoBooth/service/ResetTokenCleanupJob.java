package com.example.photoBooth.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.photoBooth.repository.PasswordResetTokenRepository;

import java.time.Instant;

@Component 
public class ResetTokenCleanupJob {
    
    private static final Logger logger = LoggerFactory.getLogger(ResetTokenCleanupJob.class);

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public ResetTokenCleanupJob(PasswordResetTokenRepository passwordResetTokenRepository){
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Scheduled(cron = "${password-reset.cleanup-cron:0 0 * * * *}")
    @Transactional 
    public void deleteExpiredTokens(){
        int deleted = passwordResetTokenRepository.deleteExpiredBefore(Instant.now());
        if (deleted > 0) {
            logger.info("Deleted {} expired password reset tokens", deleted);
        }
    }

}
