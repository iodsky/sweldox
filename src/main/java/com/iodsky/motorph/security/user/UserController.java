package com.iodsky.motorph.security.user;

import com.iodsky.motorph.common.ApiResponse;
import com.iodsky.motorph.common.BatchResponse;
import com.iodsky.motorph.common.ResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('IT')")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody UserRequest userRequest) {
        UserDto user = userMapper.toDto(userService.createUser(userRequest));
        return ResponseFactory.created("User created successfully", user);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BatchResponse>> importUsers(@RequestPart("file") MultipartFile file) {
        Integer count = userService.importUsers(file);
        return ResponseFactory.ok("Users imported successfully", new BatchResponse(count));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsers(
            @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String role
    ) {
        Page<User> page  = userService.getAllUsers(pageNo, limit, role);
        List<UserDto> data = page.getContent().stream().map(userMapper::toDto).toList();

        return ResponseFactory.ok("Users retrieved successfully", data);
    }



}
