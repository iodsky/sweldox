package com.iodsky.sweldox.payroll;

import com.iodsky.sweldox.attendance.Attendance;
import com.iodsky.sweldox.attendance.AttendanceService;
import com.iodsky.sweldox.employee.Employee;
import com.iodsky.sweldox.employee.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PayrollBuilder {

    private final EmployeeService employeeService;
    private final AttendanceService attendanceService;
    private final DeductionTypeRepository deductionTypeRepository;

    public Payroll buildPayroll(Long employeeId, LocalDate periodStart, LocalDate periodEnd, LocalDate payDate) {
        // Build context with all necessary data
        PayrollContext context = buildContext(employeeId, periodStart, periodEnd);

        // Build and return payroll entity
        return buildPayrollFromContext(context, payDate);
    }

    private PayrollContext buildContext(Long employeeId, LocalDate periodStart, LocalDate periodEnd) {
        Employee employee = employeeService.getEmployeeById(employeeId);
        List<Attendance> attendances = attendanceService.getEmployeeAttendances(employeeId, periodStart, periodEnd);
        List<Benefit> benefits = employee.getBenefits();

        BigDecimal basicSalary = employee.getBasicSalary();
        BigDecimal hourlyRate = employee.getHourlyRate();

        // Calculate hours
        BigDecimal totalHours = PayrollCalculator.calculateTotalHours(attendances);
        BigDecimal overtimeHours = PayrollCalculator.calculateOvertimeHours(attendances);
        BigDecimal regularHours = totalHours.subtract(overtimeHours);

        // Calculate pay
        BigDecimal regularPay = PayrollCalculator.calculateRegularPay(hourlyRate, regularHours);
        BigDecimal overtimePay = PayrollCalculator.calculateOvertimePay(hourlyRate, overtimeHours);
        BigDecimal grossPay = PayrollCalculator.calculateGrossPay(regularPay, overtimePay);

        // Calculate benefits
        BigDecimal totalBenefits = PayrollCalculator.calculateTotalBenefits(benefits);

        // Calculate statutory deductions
        BigDecimal sss = PayrollCalculator.calculateSssDeduction(basicSalary);
        BigDecimal philhealth = PayrollCalculator.calculatePhilhealthDeduction(basicSalary);
        BigDecimal pagibig = PayrollCalculator.calculatePagibigDeduction(basicSalary);

        // Calculate tax
        BigDecimal statutoryDeductions = PayrollCalculator.calculateTotalStatutoryDeductions(sss, philhealth, pagibig);
        BigDecimal taxableIncome = PayrollCalculator.calculateTaxableIncome(grossPay, statutoryDeductions);
        BigDecimal withholdingTax = PayrollCalculator.calculateWithholdingTax(taxableIncome);
        BigDecimal totalDeductions = withholdingTax.add(statutoryDeductions).setScale(2, RoundingMode.HALF_UP);

        // Calculate net pay
        BigDecimal netPay = PayrollCalculator.calculateNetPay(grossPay, totalBenefits, statutoryDeductions, withholdingTax);

        return PayrollContext.builder()
                .employeeId(employeeId)
                .employee(employee)
                .attendances(attendances)
                .benefits(benefits)
                .hourlyRate(hourlyRate)
                .basicSalary(basicSalary)
                .totalHours(totalHours)
                .overtimeHours(overtimeHours)
                .regularHours(regularHours)
                .regularPay(regularPay)
                .overtimePay(overtimePay)
                .grossPay(grossPay)
                .totalBenefits(totalBenefits)
                .sss(sss)
                .philhealth(philhealth)
                .pagibig(pagibig)
                .taxableIncome(taxableIncome)
                .withholdingTax(withholdingTax)
                .totalDeductions(totalDeductions)
                .netPay(netPay)
                .build();
    }

    private Payroll buildPayrollFromContext(PayrollContext context, LocalDate payDate) {
        BigDecimal dailyRate = PayrollCalculator.calculateDailyRate(context.getHourlyRate());

        // Build deduction list
        List<Deduction> deductions = buildDeductions(context);

        // Build payroll benefits
        List<PayrollBenefit> payrollBenefits = buildPayrollBenefits(context.getBenefits());

        // Determine period dates from attendances
        LocalDate periodStartDate = context.getAttendances().isEmpty() ? null :
                context.getAttendances().getFirst().getDate();
        LocalDate periodEndDate = context.getAttendances().isEmpty() ? null :
                context.getAttendances().getLast().getDate();

        // Build payroll entity
        Payroll payroll = Payroll.builder()
                .employee(context.getEmployee())
                .monthlyRate(context.getBasicSalary())
                .dailyRate(dailyRate)
                .periodStartDate(periodStartDate)
                .periodEndDate(periodEndDate)
                .payDate(payDate)
                .daysWorked(context.getAttendances().size())
                .overtime(context.getOvertimeHours())
                .grossPay(context.getGrossPay())
                .benefits(payrollBenefits)
                .totalBenefits(context.getTotalBenefits())
                .deductions(deductions)
                .totalDeductions(context.getTotalDeductions())
                .netPay(context.getNetPay())
                .build();

        deductions.forEach(d -> d.setPayroll(payroll));
        payrollBenefits.forEach(b -> b.setPayroll(payroll));

        return payroll;
    }

    private List<Deduction> buildDeductions(PayrollContext context) {
        List<Deduction> deductions = new ArrayList<>();

        deductions.add(Deduction.builder()
                .deductionType(deductionTypeRepository.findByCode("SSS").orElseThrow())
                .amount(context.getSss())
                .build());

        deductions.add(Deduction.builder()
                .deductionType(deductionTypeRepository.findByCode("PHIC").orElseThrow())
                .amount(context.getPhilhealth())
                .build());

        deductions.add(Deduction.builder()
                .deductionType(deductionTypeRepository.findByCode("HDMF").orElseThrow())
                .amount(context.getPagibig())
                .build());

        deductions.add(Deduction.builder()
                .deductionType(deductionTypeRepository.findByCode("TAX").orElseThrow())
                .amount(context.getWithholdingTax())
                .build());

        return deductions;
    }

    private List<PayrollBenefit> buildPayrollBenefits(List<Benefit> benefits) {
        return benefits.stream()
                .map(benefit -> PayrollBenefit.builder()
                        .benefitType(benefit.getBenefitType())
                        .amount(benefit.getAmount())
                        .build())
                .toList();
    }

}
