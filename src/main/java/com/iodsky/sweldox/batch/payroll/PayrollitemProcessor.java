package com.iodsky.sweldox.batch.payroll;

import com.iodsky.sweldox.payroll.Payroll;
import com.iodsky.sweldox.payroll.PayrollBuilder;
import com.iodsky.sweldox.payroll.PayrollRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class PayrollitemProcessor implements ItemProcessor<Long, Payroll> {

    private final PayrollBuilder payrollBuilder;
    private final PayrollRepository payrollRepository;

    @Value("#{jobParameters['periodStartDate']}")
    private String periodStartDateStr;

    @Value("#{jobParameters['periodEndDate']}")
    private String periodEndDateStr;

    @Value("#{jobParameters['payDate']}")
    private String payDateStr;

    @Override
    public Payroll process(Long employeeId) {
        LocalDate periodStartDate = LocalDate.parse(periodStartDateStr);
        LocalDate periodEndDate = LocalDate.parse(periodEndDateStr);
        LocalDate payDate = LocalDate.parse(payDateStr);

        // Check if payroll already exists for this employee and period
        boolean exists = payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(
                employeeId, periodStartDate, periodEndDate);

        if (exists) {
            log.warn("Payroll already exists for employee {} for period {} to {}. Skipping...",
                    employeeId, periodStartDate, periodEndDate);
            return null; // Return null to skip this item
        }

        // Build and return the payroll
        try {
            Payroll payroll = payrollBuilder.buildPayroll(employeeId, periodStartDate, periodEndDate, payDate);
            log.debug("Successfully built payroll for employee {}", employeeId);
            return payroll;
        } catch (Exception ex) {
            log.error("Failed to process payroll for employee {}. Reason: {}", employeeId, ex.getMessage());
            throw new RuntimeException("Failed to process payroll for employee " + employeeId, ex);
        }
    }

}
