package com.iodsky.motorph.leave;

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
@RequestMapping("/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveRequestService leaveService;
    private final LeaveCreditService leaveCreditService;
    private final LeaveRequestService leaveRequestService;
    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveCreditMapper leaveCreditMapper;

    @PostMapping
    public ResponseEntity<LeaveRequest> createLeave(@Valid @RequestBody LeaveRequestDto dto) {
        LeaveRequest leaveRequest = leaveService.createLeaveRequest(dto);

        return new ResponseEntity<>(leaveRequest, HttpStatus.CREATED);
    }

    @GetMapping()
    public ResponseEntity<PageDto<LeaveRequestDto>> getLeaveRequests(
            @RequestParam(defaultValue = "0") @Min(0) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        Page<LeaveRequest> page = leaveRequestService.getLeaveRequests(pageNum, limit);

        return ResponseEntity.ok(PageMapper.map(page, leaveRequestMapper::toDto));
    }

    @PreAuthorize("hasRole('HR')")
    @PatchMapping("/{leaveRequestId}")
    public ResponseEntity<LeaveRequestDto> updateLeaveStatus(
            @PathVariable String leaveRequestId,
            @Valid @RequestBody UpdateLeaveStatusDto dto) {
        LeaveRequest leaveRequest = leaveRequestService.updateLeaveStatus(leaveRequestId, dto.getStatus());

        return ResponseEntity.ok(leaveRequestMapper.toDto(leaveRequest));
    }

    @PreAuthorize("hasRole('HR')")
    @PostMapping("/credits")
    public ResponseEntity<Map<String, Integer>> importLeaveCredits(@RequestPart MultipartFile file) {
        Integer count = leaveCreditService.importLeaveCredits(file);
        return new ResponseEntity<>(Map.of("recordsCreated", count), HttpStatus.OK);
    }

    @GetMapping("/credits")
    public ResponseEntity<List<LeaveCreditDto>> getLeaveCredits() {
        List<LeaveCreditDto> credits = leaveCreditService.getLeaveCreditsByEmployeeId()
                .stream().map(leaveCreditMapper::toDto).toList();
        return ResponseEntity.ok(credits);
    }

}
