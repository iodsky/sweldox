package com.iodsky.sweldox.leave;

import com.iodsky.sweldox.common.response.*;
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
@RequestMapping("/leaves")
@RequiredArgsConstructor
@Tag(name = "Leave", description = "Leave request and leave credit management endpoints")
public class LeaveController {

    private final LeaveCreditService leaveCreditService;
    private final LeaveRequestService leaveRequestService;
    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveCreditMapper leaveCreditMapper;

    @PostMapping
    @Operation(summary = "Create leave request", description = "Submit a new leave request")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> createLeave(@Valid @RequestBody LeaveRequestDto dto) {
        LeaveRequestDto leaveRequest = leaveRequestMapper.toDto(leaveRequestService.createLeaveRequest(dto));

        return ResponseFactory.created("Leave request created successfully", leaveRequest);
    }

    @GetMapping()
    @Operation(summary = "Get leave requests", description = "Retrieve a paginated list of leave requests for the authenticated employee")
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> getLeaveRequests(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @Parameter(description = "Number of items per page (1-100)") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        Page<LeaveRequest> page = leaveRequestService.getLeaveRequests(pageNo, limit);

        List<LeaveRequestDto> leaveRequests = page.getContent().stream().map(leaveRequestMapper::toDto).toList();

        return ResponseFactory.ok("Leave requests retrieved successfully", leaveRequests, PaginationMeta.of(page));
    }

    @GetMapping("/{leaveRequestId}")
    @Operation(summary = "Get leave request by ID", description = "Retrieve a specific leave request by its ID")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> getLeaveRequestById(@Parameter(description = "Leave request ID") @PathVariable String leaveRequestId) {
        LeaveRequestDto leaveRequest = leaveRequestMapper.toDto(leaveRequestService.getLeaveRequestById(leaveRequestId));

        return ResponseFactory.ok("Leave request retrieved successfully", leaveRequest);
    }

    @PutMapping("/{leaveRequestId}")
    @Operation(summary = "Update leave request", description = "Update an existing leave request")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> updateLeaveRequest(
            @Parameter(description = "Leave request ID") @PathVariable String leaveRequestId,
            @Valid @RequestBody LeaveRequestDto dto) {
        LeaveRequestDto leaveRequest = leaveRequestMapper.toDto(leaveRequestService.updateLeaveRequest(leaveRequestId, dto));

        return ResponseFactory.ok("Leave request updated successfully", leaveRequest);
    }

    @PreAuthorize("hasRole('HR')")
    @PatchMapping("/{leaveRequestId}")
    @Operation(summary = "Update leave status", description = "Approve or reject a leave request. Requires HR role.")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> updateLeaveStatus(
            @Parameter(description = "Leave request ID") @PathVariable String leaveRequestId,
            @Valid @RequestBody UpdateLeaveStatusDto dto) {
        LeaveRequestDto leaveRequest = leaveRequestMapper.toDto(leaveRequestService.updateLeaveStatus(leaveRequestId, dto.getStatus()));

        return ResponseFactory.ok("Leave status updated successfully", leaveRequest);
    }

    @DeleteMapping("/{leaveRequestId}")
    @Operation(summary = "Delete leave request", description = "Cancel a leave request")
    public ResponseEntity<ApiResponse<DeleteResponse>> deleteLeaveRequest(@Parameter(description = "Leave request ID") @PathVariable String leaveRequestId) {
        leaveRequestService.deleteLeaveRequest(leaveRequestId);
        DeleteResponse res = new DeleteResponse("Leave Request", leaveRequestId);
        return ResponseFactory.ok("Leave request deleted successfully", res);

    }

    @PreAuthorize("hasRole('HR')")
    @PostMapping(value = "/credits", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Initialize employee leave credits",
            description = "Set up initial leave credits for an employee. Requires HR role. Returns a list of created leave credits.",
            operationId = "initializeEmployeeLeaveCredits"
    )
    public ResponseEntity<ApiResponse<List<LeaveCreditDto>>> initializeEmployeeLeaveCredits(@Valid @RequestBody InitializeEmployeeLeaveCreditsDto dto) {
        List<LeaveCreditDto> leaveCredits = leaveCreditService.initializeEmployeeLeaveCredits(dto)
                .stream()
                .map(leaveCreditMapper::toDto)
                .toList();
        return ResponseFactory.created("Leave credits created successfully", leaveCredits);
    }

    @PreAuthorize("hasRole('HR')")
    @PostMapping(value = "/credits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import leave credits from CSV",
            description = "Bulk import leave credits from a CSV file. Requires HR role. Returns the count of imported leave credits.",
            operationId = "importLeaveCredits"
    )
    public ResponseEntity<ApiResponse<BatchResponse>> importLeaveCredits(@RequestPart MultipartFile file) {
        Integer count = leaveCreditService.importLeaveCredits(file);
        return ResponseFactory.ok("Leave credits imported successfully", new BatchResponse(count));
    }

    @GetMapping("/credits")
    @Operation(summary = "Get my leave credits", description = "Retrieve leave credits for the authenticated employee")
    public ResponseEntity<ApiResponse<List<LeaveCreditDto>>> getLeaveCredits() {
        List<LeaveCreditDto> credits = leaveCreditService.getLeaveCreditsByEmployeeId()
                .stream().map(leaveCreditMapper::toDto).toList();
        return ResponseFactory.ok("Leave credits retrieved successfully", credits);
    }

    @DeleteMapping("/credits/employee/{employeeId}")
    @Operation(summary = "Delete employee leave credits", description = "Delete all leave credits for a specific employee")
    public ResponseEntity<ApiResponse<DeleteResponse>> deleteLeaveCreditsByEmployeeId(@Parameter(description = "Employee ID") @PathVariable Long employeeId) {
        leaveCreditService.deleteLeaveCreditsByEmployeeId(employeeId);
        DeleteResponse res = new DeleteResponse("Leave credit", employeeId);
        return ResponseFactory.ok("Employee leave credit deleted successfully", res);
    }

}
