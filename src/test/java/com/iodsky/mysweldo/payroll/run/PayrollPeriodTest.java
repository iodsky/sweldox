package com.iodsky.mysweldo.payroll.run;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class PayrollPeriodTest {

    @Nested
    class ValidPeriods {

        @Test
        void semiMonthlyFirstHalf() {
            assertThatNoException().isThrownBy(() ->
                    PayrollPeriod.of(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 15), PayrollFrequency.SEMI_MONTHLY)
            );
        }

        @Test
        void semiMonthlySecondHalf() {
            assertThatNoException().isThrownBy(() ->
                    PayrollPeriod.of(LocalDate.of(2025, 3, 16), LocalDate.of(2025, 3, 31), PayrollFrequency.SEMI_MONTHLY)
            );
        }

        @Test
        void monthlyFebruary() {
            assertThatNoException().isThrownBy(() ->
                    PayrollPeriod.of(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28), PayrollFrequency.MONTHLY)
            );
        }

        @Test
        void monthlyJanuary() {
            assertThatNoException().isThrownBy(() ->
                    PayrollPeriod.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), PayrollFrequency.MONTHLY)
            );
        }
    }

    @Nested
    class InvalidPeriods {

        @Test
        void endBeforeStart() {
            assertThatThrownBy(() ->
                    PayrollPeriod.of(LocalDate.of(2025, 3, 31), LocalDate.of(2025, 3, 1), PayrollFrequency.SEMI_MONTHLY)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void semiMonthlyWithMonthlySpan() {
            assertThatThrownBy(() ->
                    PayrollPeriod.of(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31), PayrollFrequency.SEMI_MONTHLY)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("SEMI_MONTHLY");
        }

        @Test
        void nullFrequency() {
            assertThatThrownBy(() ->
                    PayrollPeriod.of(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 15), null)
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class UtilityMethods {

        @Test
        void periodsPerYear() {
            assertThat(period(PayrollFrequency.SEMI_MONTHLY).periodsPerYear()).isEqualTo(24);
            assertThat(period(PayrollFrequency.MONTHLY).periodsPerYear()).isEqualTo(12);
        }

        @Test
        void annualizationFactor() {
            assertThat(period(PayrollFrequency.SEMI_MONTHLY).annualizationFactor())
                    .isEqualByComparingTo(BigDecimal.valueOf(24));
        }

        @Test
        void durationDaysIsInclusive() {
            PayrollPeriod p = PayrollPeriod.of(
                    LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 15), PayrollFrequency.SEMI_MONTHLY
            );
            assertThat(p.durationDays()).isEqualTo(15L);
        }

        private PayrollPeriod period(PayrollFrequency frequency) {
            return switch (frequency) {
                case SEMI_MONTHLY -> PayrollPeriod.of(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 15), frequency);
                case MONTHLY      -> PayrollPeriod.of(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31), frequency);
            };
        }
    }
}
