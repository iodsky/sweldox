package com.iodsky.motorph.payroll;

import jakarta.annotation.Nullable;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PayrollRequest {
    @Nullable
    private Long employeeId;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private LocalDate payDate;
}
