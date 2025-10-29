package com.iodsky.motorph.attendance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<AttendanceDto> createAttendance(@Valid @RequestBody(required = false) AttendanceDto attendanceDto) {
        Attendance attendance = attendanceService.createAttendance(attendanceDto);
        AttendanceDto dto = attendanceMapper.toDto(attendance);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('HR')")
    @GetMapping
    public ResponseEntity<List<AttendanceDto>> getAllAttendances(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        List<AttendanceDto> attendance = attendanceService.getAllAttendances(startDate, endDate)
                .stream()
                .map(attendanceMapper::toDto)
                .toList();
        return ResponseEntity.ok(attendance);
    }

    @GetMapping("/me")
    public ResponseEntity<List<AttendanceDto>> getMyAttendances(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        List<AttendanceDto> attendances = attendanceService
                .getEmployeeAttendances(null, startDate, endDate)
                .stream()
                .map(attendanceMapper::toDto)
                .toList();
        return ResponseEntity.ok(attendances);
    }

    @PreAuthorize("hasRole('HR')")
    @GetMapping("/employee/{id}")
    public ResponseEntity<List<AttendanceDto>> getEmployeeAttendancesForHR(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        List<AttendanceDto> attendances = attendanceService
                .getEmployeeAttendances(id, startDate, endDate)
                .stream()
                .map(attendanceMapper::toDto)
                .toList();
        return ResponseEntity.ok(attendances);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AttendanceDto> updateAttendance(@PathVariable UUID id, @Valid @RequestBody(required = false) AttendanceDto attendanceDto) {
        Attendance attendance = attendanceService.updateAttendance(id, attendanceDto);
        AttendanceDto dto = attendanceMapper.toDto(attendance);
        return ResponseEntity.ok(dto);
    }
}
