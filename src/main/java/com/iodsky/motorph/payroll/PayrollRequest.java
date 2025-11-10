package com.iodsky.motorph.payroll;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PayrollRequest {
    private Long employeeId;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private LocalDate payDate;
}
