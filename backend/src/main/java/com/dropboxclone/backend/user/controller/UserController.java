package com.dropboxclone.backend.user.controller;

import com.dropboxclone.backend.auth.security.AuthenticatedUser;
import com.dropboxclone.backend.user.model.User;
import com.dropboxclone.backend.user.response.GetUserResponse;
import com.dropboxclone.backend.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<GetUserResponse> getUser(Authentication authentication) {
        var requestUser = (AuthenticatedUser) authentication.getPrincipal();
        User user = userService.getUser(requestUser.id());
        GetUserResponse response = new GetUserResponse(user.getId(), user.getEmail());
        return ResponseEntity.ok(response);
    }
}
