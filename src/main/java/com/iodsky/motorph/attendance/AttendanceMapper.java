package com.iodsky.motorph.attendance;

import org.springframework.stereotype.Component;

@Component
public class AttendanceMapper {

    public AttendanceDto toDto(Attendance attendance) {
        if (attendance == null) {
            return null;
        }

        return AttendanceDto.builder()
                .id(attendance.getId())
                .employeeId(attendance.getEmployee().getId())
                .date(attendance.getDate())
                .timeIn(attendance.getTimeIn())
                .timeOut(attendance.getTimeOut())
                .totalHours(attendance.getTotalHours())
                .overtimeHours(attendance.getOvertime())
                .build();
    }
}
