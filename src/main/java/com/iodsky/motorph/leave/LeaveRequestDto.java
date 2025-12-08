package com.iodsky.motorph.leave;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class LeaveRequestDto {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String id;

    @NotNull(message = "Leave type is required")
    private String leaveType;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long employeeId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDate requestDate;

    @NotNull(message = "Start date is required")
    @FutureOrPresent
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @FutureOrPresent
    private LocalDate endDate;

    private String note;

    private String status;
}
