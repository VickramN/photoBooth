package com.example.photoBooth.service;

import com.example.photoBooth.entity.User;

public interface PasswordResetNotifier {

    void sendResetToken(User user, String rawToken);

}