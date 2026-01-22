package com.iodsky.sweldox.payroll;

import com.iodsky.sweldox.common.DateRange;
import com.iodsky.sweldox.common.DateRangeResolver;
import com.iodsky.sweldox.security.user.User;
import com.iodsky.sweldox.security.user.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private static final Logger log = LoggerFactory.getLogger(PayrollService.class);

    private final PayrollRepository payrollRepository;
    private final PayrollBuilder payrollBuilder;
    private final UserService userService;
    private final DateRangeResolver dateRangeResolver;

    public Payroll createPayroll(Long employeeId, LocalDate periodStartDate, LocalDate periodEndDate, LocalDate payDate) {

        if (payrollExistsForEmployeeAndPeriod(employeeId, periodStartDate, periodEndDate)) {
            log.warn(
                    "Payroll already exists for employee {} for period {} to {}. Skipping...",
                    employeeId, periodStartDate, periodEndDate
            );
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    String.format(
                        "Payroll already exists for employee %s for period %s to %s.",
                        employeeId, periodStartDate, periodEndDate
                    )
            );
        }

        Payroll payroll = payrollBuilder.buildPayroll(employeeId, periodStartDate, periodEndDate, payDate);

        return payrollRepository.save(payroll);
    }


    private Boolean payrollExistsForEmployeeAndPeriod(Long employeeId, LocalDate startDate, LocalDate endDate) {
        return payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(employeeId, startDate, endDate);
    }

    public Payroll getPayrollById(UUID payrollId) {
        User user = userService.getAuthenticatedUser();

        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll " + payrollId + " not found"));

        if (!user.getUserRole().getRole().equals("PAYROLL") ||
                !payroll.getEmployee().getId().equals(user.getEmployee().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have permission to access this resource");
        }

        return payroll;
    }

    public Page<Payroll> getAllPayroll(int page, int limit, LocalDate periodStartDate, LocalDate periodEndDate) {
        Pageable pageable = PageRequest.of(page, limit);
        DateRange dateRange = dateRangeResolver.resolve(periodStartDate, periodEndDate);

        return payrollRepository.findAllByPeriodStartDateBetween(dateRange.startDate(), dateRange.endDate(), pageable);
    }

    public Page<Payroll> getAllEmployeePayroll(int page, int limit, LocalDate periodStartDate, LocalDate periodEndDate) {
        User user = userService.getAuthenticatedUser();

        Pageable pageable = PageRequest.of(page, limit);
        DateRange range = dateRangeResolver.resolve(periodStartDate, periodEndDate);

        Long id = user.getEmployee().getId();
        return payrollRepository.findAllByEmployee_IdAndPeriodStartDateBetween(
                id,
                range.startDate(),
                range.endDate(),
                pageable
        );
    }

}
