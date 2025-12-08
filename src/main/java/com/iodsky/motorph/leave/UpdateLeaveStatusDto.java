package com.iodsky.motorph.leave;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateLeaveStatusDto {
    @NotNull(message = "Status is required")
    private LeaveStatus status;
}
