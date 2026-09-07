package com.iodsky.mysweldo.payroll.run;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollPeriod {

    @Column(name = "period_start_date")
    private LocalDate startDate;

    @Column(name = "period_end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payroll_frequency")
    private PayrollFrequency frequency;

    private PayrollPeriod(LocalDate startDate, LocalDate endDate, PayrollFrequency frequency) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.frequency = frequency;
    }

    public static PayrollPeriod of(LocalDate startDate, LocalDate endDate, PayrollFrequency frequency) {
        if (startDate == null || endDate == null || frequency == null) {
            throw new IllegalArgumentException("startDate, endDate, and frequency are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Period end date must not be before start date");
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        validateDuration(frequency, days);
        return new PayrollPeriod(startDate, endDate, frequency);
    }

    private static void validateDuration(PayrollFrequency frequency, long days) {
        long min, max;
        switch (frequency) {
            case SEMI_MONTHLY -> { min = 13; max = 16; }
            case MONTHLY     -> { min = 27; max = 31; }
            default -> throw new IllegalArgumentException("Unsupported frequency: " + frequency);
        }
        if (days < min || days > max) {
            throw new IllegalArgumentException(
                    "Period duration " + days + " days is invalid for " + frequency
                    + " (expected " + min + "–" + max + " days)"
            );
        }
    }

    public int periodsPerYear() {
        return switch (frequency) {
            case SEMI_MONTHLY -> 24;
            case MONTHLY      -> 12;
        };
    }

    public BigDecimal annualizationFactor() {
        return BigDecimal.valueOf(periodsPerYear());
    }

    public long durationDays() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
