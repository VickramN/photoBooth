package com.example.photoBooth.service;

import com.example.photoBooth.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevLogPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger logger = LoggerFactory.getLogger(DevLogPasswordResetNotifier.class);

    @Override
    public void sendResetToken(User user, String rawToken) {
        logger.warn("DEV ONLY - password reset token for user {}: {}", user.getId(), rawToken);
    }

}
