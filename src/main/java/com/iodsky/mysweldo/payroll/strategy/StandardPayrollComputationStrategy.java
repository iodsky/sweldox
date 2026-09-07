package com.iodsky.mysweldo.payroll.strategy;

import com.iodsky.mysweldo.attendance.AttendancePayrollSummary;
import com.iodsky.mysweldo.attendance.AttendanceService;
import com.iodsky.mysweldo.employee.Employee;
import com.iodsky.mysweldo.employee.EmployeeBenefit;
import com.iodsky.mysweldo.employee.PayType;
import com.iodsky.mysweldo.payroll.calc.PayrollCalculator;
import com.iodsky.mysweldo.payroll.calc.PayrollComputationResult;
import com.iodsky.mysweldo.payroll.calc.StatutoryRateSnapshot;
import com.iodsky.mysweldo.payroll.run.PayrollFrequency;
import com.iodsky.mysweldo.payroll.run.PayrollRun;
import com.iodsky.mysweldo.payroll.PayrollRunException;
import com.iodsky.mysweldo.overtime.OvertimeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StandardPayrollComputationStrategy implements PayrollComputationStrategy {

    private final AttendanceService attendanceService;
    private final OvertimeRequestService overtimeRequestService;
    private final PayrollCalculator payrollCalculator;
    private final PayBasisStrategyFactory payBasisStrategyFactory;

    @Override
    public PayrollComputationResult compute(Employee employee, PayrollRun payrollRun, StatutoryRateSnapshot rates) {
        AttendancePayrollSummary attendanceSummary = attendanceService.getAttendanceSummary(
                employee.getId(),
                payrollRun.getPeriod().getStartDate(),
                payrollRun.getPeriod().getEndDate()
        );

        List<EmployeeBenefit> benefits = employee.getBenefits();

        if (employee.getSalary() == null) {
            throw new PayrollRunException(
                    "No salary record found for employee: " + employee.getId()
            );
        }

        BigDecimal totalHours = attendanceService.calculateTotalHoursByEmployeeId(
                employee.getId(),
                payrollRun.getPeriod().getStartDate(),
                payrollRun.getPeriod().getEndDate()
        );

        BigDecimal approvedOvertimeHours = overtimeRequestService.calculateApprovedOvertimeHours(
                employee.getId(),
                payrollRun.getPeriod().getStartDate(),
                payrollRun.getPeriod().getEndDate()
        );

        BigDecimal standardHours = attendanceSummary.getDaysWorked().multiply(BigDecimal.valueOf(8));
        BigDecimal regularHours = totalHours.subtract(approvedOvertimeHours)
                .min(standardHours)
                .max(BigDecimal.ZERO);

        PayrollFrequency frequency = payrollRun.getPeriod().getFrequency();

        PayType payType = employee.getSalary().getPayType();
        PayBasisStrategy payBasisStrategy = payBasisStrategyFactory.getStrategy(payType);
        PayBasisResult basis = payBasisStrategy.compute(
                employee.getSalary().getRate(),
                attendanceSummary,
                regularHours,
                frequency
        );

        BigDecimal monthlyEquivalent = basis.monthlyEquivalent();

        BigDecimal overtimePay = payrollCalculator.calculateOvertimePay(basis.hourlyRate(), approvedOvertimeHours);

        BigDecimal taxableBenefits = payrollCalculator.calculateTaxableBenefits(benefits);
        BigDecimal nonTaxableBenefits = payrollCalculator.calculateNonTaxableBenefits(benefits);
        BigDecimal totalBenefits = payrollCalculator.calculateTotalBenefits(taxableBenefits, nonTaxableBenefits);

        BigDecimal grossPay = payrollCalculator.calculateGrossPay(basis.regularPay(), overtimePay, taxableBenefits);

        BigDecimal sss = payrollCalculator.calculateSssDeduction(monthlyEquivalent, rates.getSssRateTable(), frequency);
        BigDecimal philhealth = payrollCalculator.calculatePhilhealthDeduction(monthlyEquivalent, rates.getPhilhealthRateTable(), frequency);
        BigDecimal pagibig = payrollCalculator.calculatePagibigDeduction(monthlyEquivalent, rates.getPagibigRateTable(), frequency);

        BigDecimal sssEr = payrollCalculator.calculateSssEmployerContribution(monthlyEquivalent, rates.getSssRateTable(), frequency);
        BigDecimal philhealthEr = payrollCalculator.calculatePhilhealthEmployerContribution(monthlyEquivalent, rates.getPhilhealthRateTable(), frequency);
        BigDecimal pagibigEr = payrollCalculator.calculatePagibigEmployerContribution(monthlyEquivalent, rates.getPagibigRateTable(), frequency);

        BigDecimal totalStatutoryDeductions = payrollCalculator.calculateTotalStatutoryDeductions(sss, philhealth, pagibig);
        BigDecimal totalEmployerContributions = payrollCalculator.calculateTotalEmployerContributions(sssEr, philhealthEr, pagibigEr);

        BigDecimal taxableIncome = payrollCalculator.calculateTaxableIncome(grossPay, totalStatutoryDeductions);

        BigDecimal withholdingTax = payrollCalculator.calculateWithholdingTax(taxableIncome, rates.getIncomeTaxBrackets(), frequency);

        BigDecimal totalDeductions = payrollCalculator.calculateTotalDeductions(withholdingTax, totalStatutoryDeductions);

        BigDecimal netPay = payrollCalculator.calculateNetPay(
                grossPay,
                nonTaxableBenefits,
                totalStatutoryDeductions,
                withholdingTax
        );

        return PayrollComputationResult.builder()
                .employee(employee)
                .employeeBenefits(benefits)
                .payType(payType)
                .monthlyRate(monthlyEquivalent)
                .semiMonthlyRate(basis.semiMonthlyRate())
                .dailyRate(basis.dailyRate())
                .hourlyRate(basis.hourlyRate())
                .daysWorked(attendanceSummary.getDaysWorked())
                .absenceDays(attendanceSummary.getAbsenceDays())
                .tardinessMinutes(attendanceSummary.getTardinessMinutes())
                .undertimeMinutes(attendanceSummary.getUndertimeMinutes())
                .totalHours(totalHours)
                .overtimeHours(approvedOvertimeHours)
                .regularHours(regularHours)
                .absenceDeduction(basis.absenceDeduction())
                .tardinessDeduction(basis.tardinessDeduction())
                .undertimeDeduction(basis.undertimeDeduction())
                .regularPay(basis.regularPay())
                .overtimePay(overtimePay)
                .grossPay(grossPay)
                .totalBenefits(totalBenefits)
                .sss(sss)
                .philhealth(philhealth)
                .pagibig(pagibig)
                .sssEr(sssEr)
                .philhealthEr(philhealthEr)
                .pagibigEr(pagibigEr)
                .totalEmployerContributions(totalEmployerContributions)
                .taxableIncome(taxableIncome)
                .withholdingTax(withholdingTax)
                .totalDeductions(totalDeductions)
                .netPay(netPay)
                .build();
    }
}
