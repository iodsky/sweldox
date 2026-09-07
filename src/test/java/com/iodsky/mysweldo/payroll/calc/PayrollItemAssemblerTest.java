package com.iodsky.mysweldo.payroll.calc;

import com.iodsky.mysweldo.contribution.ContributionService;
import com.iodsky.mysweldo.deduction.DeductionService;
import com.iodsky.mysweldo.employee.Employee;
import com.iodsky.mysweldo.employee.EmployeeService;
import com.iodsky.mysweldo.employee.Salary;
import com.iodsky.mysweldo.payroll.run.PayrollFrequency;
import com.iodsky.mysweldo.payroll.run.PayrollPeriod;
import com.iodsky.mysweldo.payroll.run.PayrollRun;
import com.iodsky.mysweldo.payroll.PayrollRunException;
import com.iodsky.mysweldo.payroll.item.PayrollItem;
import com.iodsky.mysweldo.payroll.strategy.PayrollComputationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollItemAssemblerTest {

    @InjectMocks
    private PayrollItemAssembler assembler;

    @Mock private EmployeeService employeeService;
    @Mock private DeductionService deductionService;
    @Mock private ContributionService contributionService;
    @Mock private PayrollComputationStrategy strategy;
    @Mock private StatutoryRateSnapshot rates;

    private PayrollRun semiMonthlyRun;

    @BeforeEach
    void setUp() {
        semiMonthlyRun = PayrollRun.builder()
                .period(PayrollPeriod.of(
                        LocalDate.of(2025, 3, 1),
                        LocalDate.of(2025, 3, 15),
                        PayrollFrequency.SEMI_MONTHLY))
                .build();
    }

    private Employee employeeWithFrequency(PayrollFrequency frequency) {
        return Employee.builder()
                .id(1L)
                .salary(Salary.builder()
                        .rate(BigDecimal.valueOf(20000))
                        .payrollFrequency(frequency)
                        .build())
                .benefits(List.of())
                .build();
    }

    @Nested
    class FrequencyReconciliation {

        @Test
        void shouldProceedWhenEmployeeFrequencyMatchesRun() {
            Employee employee = employeeWithFrequency(PayrollFrequency.SEMI_MONTHLY);
            PayrollComputationResult result = mock(PayrollComputationResult.class);

            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(strategy.compute(any(), any(), any())).thenReturn(result);
            when(result.getEmployee()).thenReturn(employee);
            when(result.getEmployeeBenefits()).thenReturn(List.of());
            when(result.getOvertimeHours()).thenReturn(BigDecimal.ZERO);
            when(result.getGrossPay()).thenReturn(BigDecimal.ZERO);
            when(result.getTotalBenefits()).thenReturn(BigDecimal.ZERO);
            when(result.getTotalDeductions()).thenReturn(BigDecimal.ZERO);
            when(result.getNetPay()).thenReturn(BigDecimal.ZERO);
            when(result.getSss()).thenReturn(BigDecimal.ZERO);
            when(result.getPhilhealth()).thenReturn(BigDecimal.ZERO);
            when(result.getPagibig()).thenReturn(BigDecimal.ZERO);
            when(result.getWithholdingTax()).thenReturn(BigDecimal.ZERO);
            when(result.getSssEr()).thenReturn(BigDecimal.ZERO);
            when(result.getPhilhealthEr()).thenReturn(BigDecimal.ZERO);
            when(result.getPagibigEr()).thenReturn(BigDecimal.ZERO);

            assertThatNoException().isThrownBy(() -> assembler.buildPayroll(1L, semiMonthlyRun, rates));
            verify(strategy).compute(employee, semiMonthlyRun, rates);
        }

        @Test
        void shouldThrowWhenEmployeeFrequencyDoesNotMatchRun() {
            Employee employee = employeeWithFrequency(PayrollFrequency.MONTHLY);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);

            assertThatThrownBy(() -> assembler.buildPayroll(1L, semiMonthlyRun, rates))
                    .isInstanceOf(PayrollRunException.class)
                    .hasMessageContaining("MONTHLY")
                    .hasMessageContaining("SEMI_MONTHLY");

            verify(strategy, never()).compute(any(), any(), any());
        }

        @Test
        void shouldProceedWithWarnWhenEmployeeFrequencyIsNull() {
            Employee employee = Employee.builder()
                    .id(1L)
                    .salary(Salary.builder()
                            .rate(BigDecimal.valueOf(20000))
                            .payrollFrequency(null)
                            .build())
                    .benefits(List.of())
                    .build();
            PayrollComputationResult result = mock(PayrollComputationResult.class);

            when(employeeService.getEmployeeById(1L)).thenReturn(employee);
            when(strategy.compute(any(), any(), any())).thenReturn(result);
            when(result.getEmployee()).thenReturn(employee);
            when(result.getEmployeeBenefits()).thenReturn(List.of());
            when(result.getOvertimeHours()).thenReturn(BigDecimal.ZERO);
            when(result.getGrossPay()).thenReturn(BigDecimal.ZERO);
            when(result.getTotalBenefits()).thenReturn(BigDecimal.ZERO);
            when(result.getTotalDeductions()).thenReturn(BigDecimal.ZERO);
            when(result.getNetPay()).thenReturn(BigDecimal.ZERO);
            when(result.getSss()).thenReturn(BigDecimal.ZERO);
            when(result.getPhilhealth()).thenReturn(BigDecimal.ZERO);
            when(result.getPagibig()).thenReturn(BigDecimal.ZERO);
            when(result.getWithholdingTax()).thenReturn(BigDecimal.ZERO);
            when(result.getSssEr()).thenReturn(BigDecimal.ZERO);
            when(result.getPhilhealthEr()).thenReturn(BigDecimal.ZERO);
            when(result.getPagibigEr()).thenReturn(BigDecimal.ZERO);

            assertThatNoException().isThrownBy(() -> assembler.buildPayroll(1L, semiMonthlyRun, rates));
            verify(strategy).compute(employee, semiMonthlyRun, rates);
        }

        @Test
        void shouldThrowForEachMismatchedFrequency() {
            PayrollRun monthlyRun = PayrollRun.builder()
                    .period(PayrollPeriod.of(
                            LocalDate.of(2025, 3, 1),
                            LocalDate.of(2025, 3, 31),
                            PayrollFrequency.MONTHLY))
                    .build();

            Employee employee = employeeWithFrequency(PayrollFrequency.SEMI_MONTHLY);
            when(employeeService.getEmployeeById(1L)).thenReturn(employee);

            assertThatThrownBy(() -> assembler.buildPayroll(1L, monthlyRun, rates))
                    .isInstanceOf(PayrollRunException.class)
                    .hasMessageContaining("SEMI_MONTHLY")
                    .hasMessageContaining("MONTHLY");
        }
    }
}
