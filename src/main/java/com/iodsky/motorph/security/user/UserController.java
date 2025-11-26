package com.iodsky.motorph.security.user;

import com.iodsky.motorph.common.PageDto;
import com.iodsky.motorph.common.PageMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('IT')")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserRequest userRequest) {
        User user = userService.createUser(userRequest);
        return new ResponseEntity<>(userMapper.toDto(user), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<PageDto<UserDto>> getUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String role
    ) {
        Page<User> users = userService.getAllUsers(page, limit, role);
        return ResponseEntity.ok(PageMapper.map(users, userMapper::toDto));
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Integer>> importUsers(@RequestPart("file") MultipartFile file) {
        Integer count = userService.importUsers(file);
        return ResponseEntity.ok(Map.of("recordsCreated", count));
    }

}
