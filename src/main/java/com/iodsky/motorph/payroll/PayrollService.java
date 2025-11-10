package com.iodsky.motorph.payroll;

import com.iodsky.motorph.attendance.Attendance;
import com.iodsky.motorph.attendance.AttendanceService;
import com.iodsky.motorph.common.DateRange;
import com.iodsky.motorph.common.DateRangeResolver;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PayrollService {

    // Philhealth constants
    private static final BigDecimal PHILHEALTH_RATE = BigDecimal.valueOf(0.03);
    private static final BigDecimal PHILHEALTH_MAX_PREMIUM = BigDecimal.valueOf(1800);

    // Pagibig constants
    private static final BigDecimal PAGIBIG_EMPLOYEE_RATE_BELOW_1500 = BigDecimal.valueOf(0.01);
    private static final BigDecimal PAGIBIG_EMPLOYEE_RATE_ABOVE_1500 = BigDecimal.valueOf(0.02);
    private static final BigDecimal PAGIBIG_MAX_EMPLOYEE_CONTRIBUTION = BigDecimal.valueOf(100);

    /// SSS constants
    private static final TreeMap<BigDecimal, BigDecimal> SSS_TABLE = new TreeMap<>();
    private static final BigDecimal SSS_STARTING_MSC = BigDecimal.valueOf(3250);
    private static final BigDecimal SSS_MIN_CONTRIBUTION = BigDecimal.valueOf(135);
    private static final BigDecimal SSS_INCREMENT_RATE = BigDecimal.valueOf(22.5);

    public static final BigDecimal SEMI_MONTHLY_DIVISOR = BigDecimal.valueOf(2);

    static {
        for (int i = 0; i < 44; i++) {
            BigDecimal range = SSS_STARTING_MSC.add(BigDecimal.valueOf(i).multiply(BigDecimal.valueOf(500)));
            BigDecimal amount = SSS_MIN_CONTRIBUTION.add(SSS_INCREMENT_RATE.multiply(BigDecimal.valueOf(i)));
            SSS_TABLE.put(range, amount);
        }
    }

    private final PayrollRepository payrollRepository;
    private final EmployeeService employeeService;
    private final AttendanceService attendanceService;
    private final DeductionTypeRepository deductionTypeRepository;
    private final DateRangeResolver dateRangeResolver;

    public Payroll createPayroll(Long employeeId, LocalDate periodStartDate, LocalDate periodEndDate, LocalDate payDate) {

        // Calculate hours worked within the period
        Employee employee = employeeService.getEmployeeById(employeeId);
        BigDecimal basicSalary = employee.getCompensation().getBasicSalary();
        BigDecimal hourlyRate = employee.getCompensation().getHourlyRate();
        BigDecimal dailyRate = hourlyRate.multiply(BigDecimal.valueOf(8));
        List<Benefit> benefits = employee.getCompensation().getBenefits();
        List<Attendance> attendances = attendanceService.getEmployeeAttendances(employeeId, periodStartDate, periodEndDate);

        // Total hours
        BigDecimal totalHours = attendances.stream()
                .map(Attendance::getTotalHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Total overtime hours
        BigDecimal overtimeHours = attendances.stream()
                .map(Attendance::getOvertime)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Regular hours
        BigDecimal regularHours = totalHours.subtract(overtimeHours);

        // Regular pay
        BigDecimal regularPay = hourlyRate.multiply(regularHours);

        // Overtime pay
        BigDecimal overtimePay = hourlyRate
                .multiply(overtimeHours)
                .multiply(BigDecimal.valueOf(1.25));

        // Gross pay
        BigDecimal gross = regularPay.add(overtimePay).setScale(2, RoundingMode.HALF_UP);

        // Total Benefits
        BigDecimal meal = BigDecimal.ZERO;
        BigDecimal clothing = BigDecimal.ZERO;
        BigDecimal phone = BigDecimal.ZERO;

        for (Benefit b : benefits) {
            String type = b.getBenefitType().getId();

            if (type.equalsIgnoreCase("MEAL")) {
                meal = b.getAmount();
            }
            else if (type.equalsIgnoreCase("CLOTHING")) {
                clothing = b.getAmount();
            }
            else if (type.equalsIgnoreCase("PHONE")) {
                phone = b.getAmount();
            }
        }

        BigDecimal totalBenefits = benefits.stream()
                .map(Benefit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate statutory deductions
        BigDecimal sssDeduction = sssDeduction(basicSalary);

        BigDecimal philhealthDeduction = philhealthDeduction(basicSalary);

        BigDecimal pagibigDeduction = pagibigDeduction(basicSalary);

        BigDecimal statutoryDeductions = sssDeduction
                .add(philhealthDeduction)
                .add(pagibigDeduction)
                .setScale(2, RoundingMode.HALF_UP);

        // Calculate Taxable Income
        BigDecimal taxableIncome = gross.subtract(statutoryDeductions).setScale(2, RoundingMode.HALF_UP);

        BigDecimal withholdingTax = withholdingTax(taxableIncome);

        BigDecimal totalDeductions = withholdingTax.add(statutoryDeductions).setScale(2, RoundingMode.HALF_UP);

        // Calculate Net
        BigDecimal netPay = gross.add(totalBenefits)
                .subtract(statutoryDeductions)
                .subtract(withholdingTax)
                .setScale(2, RoundingMode.HALF_UP);

        List<Deduction> deductionList = new ArrayList<>();

        deductionList.add(
                Deduction.builder()
                        .deductionType(deductionTypeRepository.findByCode("SSS").orElseThrow())
                        .amount(sssDeduction)
                        .build()
        );

        deductionList.add(
                Deduction.builder()
                        .deductionType(deductionTypeRepository.findByCode("PHIC").orElseThrow())
                        .amount(philhealthDeduction)
                        .build()
        );

        deductionList.add(
                Deduction.builder()
                        .deductionType(deductionTypeRepository.findByCode("HDMF").orElseThrow())
                        .amount(pagibigDeduction)
                        .build()
        );

        deductionList.add(
                Deduction.builder()
                        .deductionType(deductionTypeRepository.findByCode("TAX").orElseThrow())
                        .amount(withholdingTax)
                        .build()
        );

        Payroll payroll = Payroll.builder()
                .employee(employee)
                .monthlyRate(employee.getCompensation().getBasicSalary())
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

        return payrollRepository.save(payroll);
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

    private BigDecimal philhealthDeduction(BigDecimal basicSalary) {
        BigDecimal monthlyPremium = basicSalary.multiply(PHILHEALTH_RATE)
                .min(PHILHEALTH_MAX_PREMIUM);

        BigDecimal employeeShare = monthlyPremium.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        return employeeShare.divide(SEMI_MONTHLY_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal pagibigDeduction(BigDecimal basicSalary) {
        BigDecimal contribution;
        if (basicSalary.compareTo(BigDecimal.valueOf(1500)) > 0) {
            contribution = basicSalary.multiply(PAGIBIG_EMPLOYEE_RATE_ABOVE_1500);
        } else {
            contribution = basicSalary.multiply(PAGIBIG_EMPLOYEE_RATE_BELOW_1500);
        }

        return contribution.min(PAGIBIG_MAX_EMPLOYEE_CONTRIBUTION)
                .divide(SEMI_MONTHLY_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sssDeduction(BigDecimal basicSalary) {
        if (basicSalary.compareTo(new BigDecimal("3250")) < 0) {
            return SSS_MIN_CONTRIBUTION.divide(SEMI_MONTHLY_DIVISOR, 2, RoundingMode.HALF_UP);
        }

        var entry = SSS_TABLE.floorEntry(basicSalary);
        if (entry == null) {
            return BigDecimal.ZERO;
        }

        return entry.getValue().divide(SEMI_MONTHLY_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal withholdingTax(BigDecimal taxableIncome) {
        BigDecimal tax;

        if (taxableIncome.compareTo(new BigDecimal("20832")) <= 0) {
            tax = BigDecimal.ZERO;

        } else if (taxableIncome.compareTo(new BigDecimal("33332")) <= 0) {
            tax = taxableIncome.subtract(new BigDecimal("20833"))
                    .multiply(new BigDecimal("0.20"));

        } else if (taxableIncome.compareTo(new BigDecimal("66666")) <= 0) {
            tax = new BigDecimal("2500")
                    .add(taxableIncome.subtract(new BigDecimal("33333"))
                            .multiply(new BigDecimal("0.25")));

        } else if (taxableIncome.compareTo(new BigDecimal("166666")) <= 0) {
            tax = new BigDecimal("10833")
                    .add(taxableIncome.subtract(new BigDecimal("66667"))
                            .multiply(new BigDecimal("0.30")));

        } else if (taxableIncome.compareTo(new BigDecimal("666666")) <= 0) {
            tax = new BigDecimal("40833.33")
                    .add(taxableIncome.subtract(new BigDecimal("166667"))
                            .multiply(new BigDecimal("0.32")));

        } else {
            tax = new BigDecimal("200833.33")
                    .add(taxableIncome.subtract(new BigDecimal("666667"))
                            .multiply(new BigDecimal("0.35")));
        }

        return tax.divide(SEMI_MONTHLY_DIVISOR, 2, RoundingMode.HALF_UP);
    }

}
