package com.iodsky.sweldox.security.user;

import com.iodsky.sweldox.common.response.ApiResponse;
import com.iodsky.sweldox.common.response.BatchResponse;
import com.iodsky.sweldox.common.response.PaginationMeta;
import com.iodsky.sweldox.common.response.ResponseFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Users", description = "User account management endpoints")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create user account",
            description = "Create a new user account. Requires IT role.",
            operationId = "createUser"
    )
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody UserRequest userRequest) {
        UserDto user = userMapper.toDto(userService.createUser(userRequest));
        return ResponseFactory.created("User created successfully", user);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import users from CSV",
            description = "Bulk import user accounts from a CSV file. Requires IT role. Returns the count of imported users.",
            operationId = "importUsers"
    )
    public ResponseEntity<ApiResponse<BatchResponse>> importUsers(@RequestPart("file") MultipartFile file) {
        Integer count = userService.importUsers(file);
        return ResponseFactory.ok("Users imported successfully", new BatchResponse(count));
    }

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieve a paginated list of user accounts with optional role filtering. Requires IT role.")
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsers(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @Parameter(description = "Number of items per page (1-100)") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @Parameter(description = "Filter by role") @RequestParam(required = false) String role
    ) {
        Page<User> page  = userService.getAllUsers(pageNo, limit, role);
        List<UserDto> data = page.getContent().stream().map(userMapper::toDto).toList();

        return ResponseFactory.ok("Users retrieved successfully", data, PaginationMeta.of(page));
    }



}
