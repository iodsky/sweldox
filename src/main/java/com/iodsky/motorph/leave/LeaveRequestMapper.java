package com.iodsky.motorph.leave;

import org.springframework.stereotype.Component;

@Component
public class LeaveRequestMapper {

    public LeaveRequestDto toDto(LeaveRequest leaveRequest) {
        return LeaveRequestDto.builder()
                .id(leaveRequest.getId())
                .employeeId(leaveRequest.getEmployee().getId())
                .requestDate(leaveRequest.getRequestDate())
                .leaveType(leaveRequest.getLeaveType().toString())
                .startDate(leaveRequest.getStartDate())
                .endDate(leaveRequest.getEndDate())
                .note(leaveRequest.getNote())
                .status(leaveRequest.getLeaveStatus().toString())
                .build();
    }

}
