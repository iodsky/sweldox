package com.iodsky.sweldox.employee;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class EmploymentDetailsRequest {

    private Long supervisorId;

    @NotNull(message = "Position is required")
    private String positionId;

    @NotNull(message = "Department is required")
    private String departmentId;

    @NotNull(message = "Status is required")
    private Status status;

    @NotNull(message = "Start shift is required")
    private LocalTime startShift;

    @NotNull(message = "End shift is required")
    private LocalTime endShift;
}
