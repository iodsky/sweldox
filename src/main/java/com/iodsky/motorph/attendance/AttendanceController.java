package com.iodsky.motorph.attendance;

import com.iodsky.motorph.common.ApiResponse;
import com.iodsky.motorph.common.ResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/attendances")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceMapper attendanceMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<AttendanceDto>> createAttendance(@Valid @RequestBody(required = false) AttendanceDto attendanceDto) {
        Attendance attendance = attendanceService.createAttendance(attendanceDto);
        AttendanceDto dto = attendanceMapper.toDto(attendance);
        return ResponseFactory.created("Attendance created successfully", dto);
    }

    @PreAuthorize("hasRole('HR')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AttendanceDto>>> getAllAttendances(
            @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        Page<Attendance> page = attendanceService.getAllAttendances(pageNo, limit, startDate, endDate);

        List<AttendanceDto> data = page.getContent().stream().map(attendanceMapper::toDto).toList();

        return ResponseFactory.ok("Attendances retrieved successfully", data);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<AttendanceDto>>> getMyAttendances(
            @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        Page<Attendance> page = attendanceService
                .getEmployeeAttendances(pageNo, limit, null, startDate, endDate);

        List<AttendanceDto> data = page.getContent().stream().map(attendanceMapper::toDto).toList();

        return ResponseFactory.ok("Attendances retrieved successfully", data);
    }

    @PreAuthorize("hasRole('HR')")
    @GetMapping("/employee/{id}")
    public ResponseEntity<ApiResponse<List<AttendanceDto>>> getEmployeeAttendancesForHR(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        Page<Attendance> page = attendanceService
                .getEmployeeAttendances(pageNo, limit, id, startDate, endDate);

        List<AttendanceDto> data = page.getContent().stream().map(attendanceMapper::toDto).toList();

        return ResponseFactory.ok("Attendances retrieved successfully", data);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AttendanceDto>> updateAttendance(@PathVariable UUID id, @Valid @RequestBody(required = false) AttendanceDto attendanceDto) {
        Attendance attendance = attendanceService.updateAttendance(id, attendanceDto);
        AttendanceDto dto = attendanceMapper.toDto(attendance);
        return ResponseFactory.ok("Attendance updated successfully", dto);
    }
}
