package com.iodsky.motorph.payroll;

import com.iodsky.motorph.attendance.Attendance;
import com.iodsky.motorph.attendance.AttendanceService;
import com.iodsky.motorph.common.DateRange;
import com.iodsky.motorph.common.DateRangeResolver;
import com.iodsky.motorph.common.exception.ConflictException;
import com.iodsky.motorph.common.exception.ForbiddenException;
import com.iodsky.motorph.common.exception.NotFoundException;
import com.iodsky.motorph.common.exception.UnauthorizedException;
import com.iodsky.motorph.employee.EmployeeService;
import com.iodsky.motorph.employee.model.Employee;
import com.iodsky.motorph.payroll.model.Benefit;
import com.iodsky.motorph.payroll.model.Deduction;
import com.iodsky.motorph.payroll.model.Payroll;
import com.iodsky.motorph.security.user.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private static final Logger log = LoggerFactory.getLogger(PayrollService.class);

    private final PayrollRepository payrollRepository;
    private final EmployeeService employeeService;
    private final AttendanceService attendanceService;
    private final DeductionTypeRepository deductionTypeRepository;
    private final DateRangeResolver dateRangeResolver;

    public Payroll createPayroll(Long employeeId, LocalDate periodStartDate, LocalDate periodEndDate, LocalDate payDate) {

        if (payrollExistsForEmployeeAndPeriod(employeeId, periodStartDate, periodEndDate)) {
            log.warn(
                    "Payroll already exists for employee {} for period {} to {}. Skipping...",
                    employeeId, periodStartDate, periodEndDate
            );
            throw new ConflictException(
                    String.format(
                        "Payroll already exists for employee %s for period %s to %s.",
                        employeeId, periodStartDate, periodEndDate
                    )
            );
        }

        Payroll payroll = buildPayroll(employeeId, periodStartDate, periodEndDate, payDate);

        return payrollRepository.save(payroll);
    }

    public Integer createPayrollBatch(LocalDate periodStartDate, LocalDate periodEndDate, LocalDate payDate) {

        List<Long> ids = employeeService.getAllActiveEmployeeIds();
        List<Payroll> payrolls = new ArrayList<>();

        for (Long id : ids) {
            if (payrollExistsForEmployeeAndPeriod(id, periodStartDate, periodEndDate)) {
                log.warn("Skipping existing payroll for employee {}", id);
                continue;
            }
            try {
                Payroll payroll = buildPayroll(id, periodStartDate, periodEndDate, payDate);
                payrolls.add(payroll);
            } catch (Exception ex) {
                log.error("Failed to process payroll for employee {}. Reason: {}", id, ex.getMessage());
            }
        }

        payrollRepository.saveAll(payrolls);

        return payrolls.size();
    }

    private Payroll buildPayroll(Long employeeId, LocalDate periodStartDate, LocalDate periodEndDate, LocalDate payDate) {

        Employee employee = employeeService.getEmployeeById(employeeId);
        BigDecimal basicSalary = employee.getCompensation().getBasicSalary();
        BigDecimal hourlyRate = employee.getCompensation().getHourlyRate();
        BigDecimal dailyRate = PayrollCalculator.calculateDailyRate(hourlyRate);
        List<Benefit> benefits = employee.getCompensation().getBenefits();
        List<Attendance> attendances = attendanceService.getEmployeeAttendances(employeeId, periodStartDate, periodEndDate);

        // Calculate hours
        BigDecimal totalHours = PayrollCalculator.calculateTotalHours(attendances);
        BigDecimal overtimeHours = PayrollCalculator.calculateOvertimeHours(attendances);
        BigDecimal regularHours = totalHours.subtract(overtimeHours);

        // Calculate pay
        BigDecimal regularPay = PayrollCalculator.calculateRegularPay(hourlyRate, regularHours);
        BigDecimal overtimePay = PayrollCalculator.calculateOvertimePay(hourlyRate, overtimeHours);
        BigDecimal gross = PayrollCalculator.calculateGrossPay(regularPay, overtimePay);

        // Calculate benefits
        BigDecimal totalBenefits = PayrollCalculator.calculateTotalBenefits(benefits);

        // Calculate statutory deductions
        BigDecimal sssDeduction = PayrollCalculator.calculateSssDeduction(basicSalary);
        BigDecimal philhealthDeduction = PayrollCalculator.calculatePhilhealthDeduction(basicSalary);
        BigDecimal pagibigDeduction = PayrollCalculator.calculatePagibigDeduction(basicSalary);
        BigDecimal statutoryDeductions = PayrollCalculator.calculateTotalStatutoryDeductions(
                sssDeduction, philhealthDeduction, pagibigDeduction);

        // Calculate tax
        BigDecimal taxableIncome = PayrollCalculator.calculateTaxableIncome(gross, statutoryDeductions);
        BigDecimal withholdingTax = PayrollCalculator.calculateWithholdingTax(taxableIncome);
        BigDecimal totalDeductions = withholdingTax.add(statutoryDeductions).setScale(2, RoundingMode.HALF_UP);

        // Calculate net pay
        BigDecimal netPay = PayrollCalculator.calculateNetPay(gross, totalBenefits, statutoryDeductions, withholdingTax);

        // Build deduction list
        List<Deduction> deductionList = buildDeductionList(sssDeduction, philhealthDeduction, pagibigDeduction, withholdingTax);

        // Build payroll entity
        Payroll payroll = Payroll.builder()
                .employee(employee)
                .monthlyRate(basicSalary)
                .dailyRate(dailyRate)
                .periodStartDate(periodStartDate)
                .periodEndDate(periodEndDate)
                .payDate(payDate)
                .daysWorked(attendances.size())
                .overtime(overtimeHours)
                .grossPay(gross)
                .totalBenefits(totalBenefits)
                .deductions(deductionList)
                .totalDeductions(totalDeductions)
                .netPay(netPay)
                .build();

        deductionList.forEach(d -> d.setPayroll(payroll));

        return payroll;
    }

    private List<Deduction> buildDeductionList(BigDecimal sss, BigDecimal philhealth, BigDecimal pagibig, BigDecimal tax) {
        List<Deduction> deductions = new ArrayList<>();

        deductions.add(Deduction.builder()
                .deductionType(deductionTypeRepository.findByCode("SSS").orElseThrow())
                .amount(sss)
                .build());

        deductions.add(Deduction.builder()
                .deductionType(deductionTypeRepository.findByCode("PHIC").orElseThrow())
                .amount(philhealth)
                .build());

        deductions.add(Deduction.builder()
                .deductionType(deductionTypeRepository.findByCode("HDMF").orElseThrow())
                .amount(pagibig)
                .build());

        deductions.add(Deduction.builder()
                .deductionType(deductionTypeRepository.findByCode("TAX").orElseThrow())
                .amount(tax)
                .build());

        return deductions;
    }

    private Boolean payrollExistsForEmployeeAndPeriod(Long employeeId, LocalDate startDate, LocalDate endDate) {
        return payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(employeeId, startDate, endDate);
    }

    public Payroll getPayrollById(UUID payrollId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof User user)) {
            throw new UnauthorizedException("Authentication required");
        }

        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new NotFoundException("Payroll " + payrollId + " not found"));

        if (!user.getUserRole().getRole().equals("PAYROLL") ||
                !payroll.getEmployee().getId().equals(user.getEmployee().getId())) {
            throw new ForbiddenException("You are unauthorized to access this resource");
        }

        return payroll;
    }

    public List<Payroll> getAllPayroll(LocalDate periodStartDate, LocalDate periodEndDate) {
        DateRange dateRange = dateRangeResolver.resolve(periodStartDate, periodEndDate);

        return payrollRepository.findAllByPeriodStartDateBetween(dateRange.startDate(), dateRange.endDate());
    }

    public List<Payroll> getAllEmployeePayroll(LocalDate periodStartDate, LocalDate periodEndDate) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof User user)) {
            throw new UnauthorizedException("Authentication required");
        }

        DateRange range = dateRangeResolver.resolve(periodStartDate, periodEndDate);

        Long id = user.getEmployee().getId();
        return payrollRepository.findAllByEmployee_IdAndPeriodStartDateBetween(
                id,
                range.startDate(),
                range.endDate()
        );
    }

}
