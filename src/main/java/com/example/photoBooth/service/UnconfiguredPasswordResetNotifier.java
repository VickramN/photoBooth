package com.example.photoBooth.service;

import com.example.photoBooth.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev")
public class UnconfiguredPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger logger = LoggerFactory.getLogger(UnconfiguredPasswordResetNotifier.class);

    @Override
    public void sendResetToken(User user, String rawToken) {
        logger.warn("Password reset requested for user {} but no delivery is configured; token was not sent",
                user.getId());
    }

}
