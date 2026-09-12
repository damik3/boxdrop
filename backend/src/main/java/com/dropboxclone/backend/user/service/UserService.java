package com.dropboxclone.backend.user.service;

import com.dropboxclone.backend.user.model.User;
import com.dropboxclone.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getUser(String userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
    }
}
