package com.iodsky.motorph.attendance;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class AttendanceDto {

    private UUID id;

    @NotNull
    private Long employeeId;

    @PastOrPresent
    private LocalDate date;

    private LocalTime timeIn;

    private LocalTime timeOut;

    private BigDecimal totalHours;

    private BigDecimal overtimeHours;
}
