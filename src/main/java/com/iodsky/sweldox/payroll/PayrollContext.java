package com.iodsky.sweldox.payroll;

import com.iodsky.sweldox.attendance.Attendance;
import com.iodsky.sweldox.employee.Employee;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class PayrollContext {
    private Long employeeId;
    private Employee employee;
    private List<Attendance> attendances;
    private List<Benefit> benefits;

    private BigDecimal hourlyRate;
    private BigDecimal basicSalary;

    private BigDecimal totalHours;
    private BigDecimal overtimeHours;
    private BigDecimal regularHours;

    private BigDecimal regularPay;
    private BigDecimal overtimePay;
    private BigDecimal grossPay;

    private BigDecimal totalBenefits;

    private BigDecimal sss;
    private BigDecimal philhealth;
    private BigDecimal pagibig;

    private BigDecimal taxableIncome;
    private BigDecimal withholdingTax;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;
}

