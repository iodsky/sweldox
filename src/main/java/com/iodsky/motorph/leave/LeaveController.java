package com.iodsky.motorph.leave;

import com.iodsky.motorph.common.*;
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
import java.util.Map;

@RestController
@RequestMapping("/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveCreditService leaveCreditService;
    private final LeaveRequestService leaveRequestService;
    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveCreditMapper leaveCreditMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<LeaveRequestDto>> createLeave(@Valid @RequestBody LeaveRequestDto dto) {
        LeaveRequestDto leaveRequest = leaveRequestMapper.toDto(leaveRequestService.createLeaveRequest(dto));

        return ResponseFactory.created("Leave request created successfully", leaveRequest);
    }

    @GetMapping()
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> getLeaveRequests(
            @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        Page<LeaveRequest> page = leaveRequestService.getLeaveRequests(pageNo, limit);

        List<LeaveRequestDto> leaveRequests = page.getContent().stream().map(leaveRequestMapper::toDto).toList();

        return ResponseFactory.ok("Leave requests retrieved successfully", leaveRequests);
    }

    @GetMapping("/{leaveRequestId}")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> getLeaveRequestById(@PathVariable String leaveRequestId) {
        LeaveRequestDto leaveRequest = leaveRequestMapper.toDto(leaveRequestService.getLeaveRequestById(leaveRequestId));

        return ResponseFactory.ok("Leave request retrieved successfully", leaveRequest);
    }

    @PutMapping("/{leaveRequestId}")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> updateLeaveRequest(
            @PathVariable String leaveRequestId,
            @Valid @RequestBody LeaveRequestDto dto) {
        LeaveRequestDto leaveRequest = leaveRequestMapper.toDto(leaveRequestService.updateLeaveRequest(leaveRequestId, dto));

        return ResponseFactory.ok("Leave request updated successfully", leaveRequest);
    }

    @PreAuthorize("hasRole('HR')")
    @PatchMapping("/{leaveRequestId}")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> updateLeaveStatus(
            @PathVariable String leaveRequestId,
            @Valid @RequestBody UpdateLeaveStatusDto dto) {
        LeaveRequestDto leaveRequest = leaveRequestMapper.toDto(leaveRequestService.updateLeaveStatus(leaveRequestId, dto.getStatus()));

        return ResponseFactory.ok("Leave status updated successfully", leaveRequest);
    }

    @DeleteMapping("/{leaveRequestId}")
    public ResponseEntity<ApiResponse<DeleteResponse>> deleteLeaveRequest(@PathVariable String leaveRequestId) {
        leaveRequestService.deleteLeaveRequest(leaveRequestId);
        DeleteResponse res = new DeleteResponse("Leave Request", leaveRequestId);
        return ResponseFactory.ok("Leave request deleted successfully", res);

    }

    @PreAuthorize("hasRole('HR')")
    @PostMapping(value = "/credits", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<LeaveCreditDto>>> initializeEmployeeLeaveCredits(@Valid @RequestBody InitializeEmployeeLeaveCreditsDto dto) {
        List<LeaveCreditDto> leaveCredits = leaveCreditService.initializeEmployeeLeaveCredits(dto)
                .stream()
                .map(leaveCreditMapper::toDto)
                .toList();
        return ResponseFactory.created("Leave credits created successfully", leaveCredits);
    }

    @PreAuthorize("hasRole('HR')")
    @PostMapping(value = "/credits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BatchResponse>> importLeaveCredits(@RequestPart MultipartFile file) {
        Integer count = leaveCreditService.importLeaveCredits(file);
        return ResponseFactory.ok("Leave credits imported successfully", new BatchResponse(count));
    }

    @GetMapping("/credits")
    public ResponseEntity<ApiResponse<List<LeaveCreditDto>>> getLeaveCredits() {
        List<LeaveCreditDto> credits = leaveCreditService.getLeaveCreditsByEmployeeId()
                .stream().map(leaveCreditMapper::toDto).toList();
        return ResponseFactory.ok("Leave credits retrieved successfully", credits);
    }

    @DeleteMapping("/credits/employee/{employeeId}")
    public ResponseEntity<ApiResponse<DeleteResponse>> deleteLeaveCreditsByEmployeeId(@PathVariable Long employeeId) {
        leaveCreditService.deleteLeaveCreditsByEmployeeId(employeeId);
        DeleteResponse res = new DeleteResponse("Leave credit", employeeId);
        return ResponseFactory.ok("Employee leave credit deleted successfully", res);
    }

}
