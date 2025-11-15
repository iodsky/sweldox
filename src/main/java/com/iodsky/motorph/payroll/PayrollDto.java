package com.iodsky.motorph.payroll;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PayrollDto {

    private UUID id;
    private Long employeeId;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private LocalDate payDate;
    private int daysWorked;
    private BigDecimal overtime;
    private BigDecimal monthlyRate;
    private BigDecimal dailyRate;
    private BigDecimal grossPay;

    private BenefitsDto benefits;
    private DeductionsDto deductions;

    private BigDecimal netPay;

}

@Data
@Builder
class BenefitsDto {
    private BigDecimal mealAllowance;
    private BigDecimal clothingAllowance;
    private BigDecimal phoneAllowance;
    private BigDecimal totalBenefits;
}

@Data
@Builder
class DeductionsDto {
    private BigDecimal sssDeduction;
    private BigDecimal philhealthDeduction;
    private BigDecimal pagibigDeduction;
    private BigDecimal withholdingTax;
    private BigDecimal totalDeductions;
}
