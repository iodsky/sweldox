package com.iodsky.motorph.payroll;

import com.iodsky.motorph.payroll.model.Benefit;
import com.iodsky.motorph.payroll.model.Deduction;
import com.iodsky.motorph.payroll.model.Payroll;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PayrollMapper {

    public PayrollDto toDto(Payroll payroll) {
        if (payroll == null) return null;

        return PayrollDto.builder()
                .id(payroll.getId())
                .employeeId(payroll.getEmployee().getId())
                .periodStartDate(payroll.getPeriodStartDate())
                .periodEndDate(payroll.getPeriodEndDate())
                .payDate(payroll.getPayDate())
                .daysWorked(payroll.getDaysWorked())
                .overtime(payroll.getOvertime())
                .monthlyRate(payroll.getMonthlyRate())
                .dailyRate(payroll.getDailyRate())
                .grossPay(payroll.getGrossPay())
                .mealAllowance(getBenefitAmount(payroll, "MEAL"))
                .phoneAllowance(getBenefitAmount(payroll, "PHONE"))
                .clothingAllowance(getBenefitAmount(payroll, "CLOTHING"))
                .totalBenefits(payroll.getTotalBenefits())
                .sssDeduction(getDeductionAmount(payroll, "SSS"))
                .philhealthDeduction(getDeductionAmount(payroll, "PHIC"))
                .pagibigDeduction(getDeductionAmount(payroll, "HDMF"))
                .withholdingTax(getDeductionAmount(payroll, "TAX"))
                .totalDeductions(payroll.getTotalDeductions())
                .netPay(payroll.getNetPay())
                .build();
    }

    private BigDecimal getDeductionAmount(Payroll payroll, String type) {
        return payroll.getDeductions().stream()
                .filter(d -> d.getDeductionType().getCode().equalsIgnoreCase(type))
                .map(Deduction::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal getBenefitAmount(Payroll payroll, String type) {
        return payroll.getEmployee().getCompensation().getBenefits().stream()
                .filter(d -> d.getBenefitType().getId().equalsIgnoreCase(type))
                .map(Benefit::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

}
