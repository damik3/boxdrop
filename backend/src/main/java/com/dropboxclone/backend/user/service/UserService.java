package com.dropboxclone.backend.user.service;

import com.dropboxclone.backend.common.ApiError;
import com.dropboxclone.backend.user.model.User;
import com.dropboxclone.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getUser(String userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(ApiError.ACCOUNT_NOT_FOUND::exception);
    }
}
