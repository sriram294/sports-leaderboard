package com.org.playboard.controller.user;

import com.org.playboard.dto.user.UpdateAvatarRequest;
import com.org.playboard.dto.user.UpdateUserRequest;
import com.org.playboard.dto.user.UserDto;
import com.org.playboard.service.user.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserDto getMe(@AuthenticationPrincipal UUID userId) {
        return userService.getById(userId);
    }

    @PatchMapping("/me")
    public UserDto updateMe(@AuthenticationPrincipal UUID userId, @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateDisplayName(userId, request.displayName());
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserDto uploadPhoto(@AuthenticationPrincipal UUID userId, @RequestParam("file") MultipartFile file) {
        return userService.updatePhoto(userId, file);
    }

    @PatchMapping("/me/avatar")
    public UserDto updateAvatar(@AuthenticationPrincipal UUID userId, @Valid @RequestBody UpdateAvatarRequest request) {
        return userService.updateAvatar(userId, request.avatarId());
    }
}
