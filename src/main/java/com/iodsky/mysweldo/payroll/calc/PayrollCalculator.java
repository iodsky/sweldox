package com.iodsky.mysweldo.payroll.calc;

import com.iodsky.mysweldo.employee.EmployeeBenefit;
import com.iodsky.mysweldo.pagIbig.PagibigRate;
import com.iodsky.mysweldo.philhealth.PhilhealthRate;
import com.iodsky.mysweldo.sss.SssRate;
import com.iodsky.mysweldo.payroll.run.PayrollFrequency;
import com.iodsky.mysweldo.payroll.PayrollRunException;
import com.iodsky.mysweldo.tax.TaxBracket;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class PayrollCalculator {

    public static final BigDecimal STANDARD_WORK_HOURS_PER_DAY = BigDecimal.valueOf(8);

    private static final BigDecimal SEMI_MONTHLY_PERIODS_PER_MONTH = BigDecimal.valueOf(2);
    public static final BigDecimal AVERAGE_WORKING_DAYS_PER_MONTH = BigDecimal.valueOf(21.75);
    private static final BigDecimal OVERTIME_MULTIPLIER = BigDecimal.valueOf(1.25);

    public BigDecimal calculatePeriodRate(BigDecimal monthlyRate, PayrollFrequency frequency) {
        return monthlyRate.divide(monthlyConversionFactor(frequency), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateDailyRate(BigDecimal monthlyRate) {
        return monthlyRate.divide(AVERAGE_WORKING_DAYS_PER_MONTH, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateHourlyRate(BigDecimal dailyRate) {
        return dailyRate.divide(STANDARD_WORK_HOURS_PER_DAY, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateMonthlyEquivalentFromDailyRate(BigDecimal dailyRate) {
        return dailyRate.multiply(AVERAGE_WORKING_DAYS_PER_MONTH).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateDailyRateFromHourlyRate(BigDecimal hourlyRate) {
        return hourlyRate.multiply(STANDARD_WORK_HOURS_PER_DAY).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateDailyBasisPay(BigDecimal dailyRate, BigDecimal daysWorked) {
        return dailyRate.multiply(daysWorked).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateHourlyBasisPay(BigDecimal hourlyRate, BigDecimal regularHours) {
        return hourlyRate.multiply(regularHours).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateOvertimePay(BigDecimal hourlyRate, BigDecimal overtimeHours) {
        return hourlyRate
                .multiply(overtimeHours)
                .multiply(OVERTIME_MULTIPLIER);
    }

    public BigDecimal calculateTaxableBenefits(List<EmployeeBenefit> benefits) {
        return benefits.stream()
                .map(EmployeeBenefit::getTaxableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateNonTaxableBenefits(List<EmployeeBenefit> benefits) {
        return benefits.stream()
                .map(EmployeeBenefit::getNonTaxableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateTotalBenefits(BigDecimal taxableBenefits, BigDecimal nonTaxableBenefits) {
        return taxableBenefits.add(nonTaxableBenefits).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateGrossPay(BigDecimal regularPay, BigDecimal overtimePay, BigDecimal taxableBenefits) {
        return regularPay.add(overtimePay)
                .add(taxableBenefits)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculatePhilhealthDeduction(BigDecimal basicSalary, PhilhealthRate config, PayrollFrequency frequency) {
        if (basicSalary.compareTo(config.getMinSalaryFloor()) <= 0) {
            // Fixed contribution is equally shared: divide by 2 for employee share, then by period divisor
            return config.getFixedContribution()
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                    .divide(periodicDivisor(frequency), 2, RoundingMode.HALF_UP);
        }

        BigDecimal cappedSalary = basicSalary.min(config.getMaxSalaryCap());

        BigDecimal monthlyPremium = cappedSalary.multiply(config.getPremiumRate());

        // Premium is equally shared: divide by 2 for employee share, then by period divisor
        return monthlyPremium
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
                .divide(periodicDivisor(frequency), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculatePagibigDeduction(BigDecimal basicSalary, PagibigRate config, PayrollFrequency frequency) {
        BigDecimal monthlySalary = basicSalary.min(config.getMaxSalaryCap());
        BigDecimal rate;

        if (monthlySalary.compareTo(config.getLowIncomeThreshold()) <= 0) {
            rate = config.getLowIncomeEmployeeRate();
        } else {
            rate = config.getEmployeeRate();
        }

        BigDecimal monthlyContribution = monthlySalary.multiply(rate);
        return monthlyContribution.divide(periodicDivisor(frequency), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateSssDeduction(BigDecimal basicSalary, SssRate sssRateTable, PayrollFrequency frequency) {
        SssRate.SalaryBracket bracket = sssRateTable.findBracket(basicSalary);
        BigDecimal monthlyContribution = bracket.getMsc().multiply(sssRateTable.getEmployeeRate());
        return monthlyContribution.divide(periodicDivisor(frequency), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateTotalStatutoryDeductions(BigDecimal sss, BigDecimal philhealth, BigDecimal pagibig) {
        return sss.add(philhealth).add(pagibig).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateWithholdingTax(BigDecimal periodicTaxableIncome, List<TaxBracket> taxBrackets, PayrollFrequency frequency) {
        BigDecimal factor = monthlyConversionFactor(frequency);
        BigDecimal monthlyTaxableIncome = periodicTaxableIncome.multiply(factor).setScale(2, RoundingMode.HALF_UP);

        TaxBracket bracket = taxBrackets.stream()
                .filter(b -> monthlyTaxableIncome.compareTo(b.getMinIncome()) >= 0
                        && (b.getMaxIncome() == null
                        || monthlyTaxableIncome.compareTo(b.getMaxIncome()) <= 0))
                .findFirst()
                .orElseThrow(() -> new PayrollRunException(
                        "Income tax bracket not found for monthly income: " + monthlyTaxableIncome
                ));

        BigDecimal excessAmount = monthlyTaxableIncome
                .subtract(bracket.getThreshold())
                .max(BigDecimal.ZERO);

        BigDecimal monthlyTax = bracket.getBaseTax()
                .add(excessAmount.multiply(bracket.getMarginalRate()));

        return monthlyTax.divide(factor, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal monthlyConversionFactor(PayrollFrequency frequency) {
        return switch (frequency) {
            case SEMI_MONTHLY -> BigDecimal.valueOf(2);
            case MONTHLY -> BigDecimal.ONE;
        };
    }

    public BigDecimal calculateTotalDeductions(BigDecimal withholdingTax, BigDecimal totalStatutoryDeductions) {
        return withholdingTax.add(totalStatutoryDeductions).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateSssEmployerContribution(BigDecimal basicSalary, SssRate config, PayrollFrequency frequency) {
        SssRate.SalaryBracket bracket = config.findBracket(basicSalary);
        BigDecimal monthlyContribution = bracket.getMsc().multiply(config.getEmployerRate());
        return monthlyContribution.divide(periodicDivisor(frequency), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculatePhilhealthEmployerContribution(BigDecimal basicSalary, PhilhealthRate config, PayrollFrequency frequency) {
        // PhilHealth premium is equally shared between employer and employee
        // Employer contribution equals employee deduction
        return calculatePhilhealthDeduction(basicSalary, config, frequency);
    }

    public BigDecimal calculatePagibigEmployerContribution(BigDecimal basicSalary, PagibigRate config, PayrollFrequency frequency) {
        // Employer always uses the flat employer_rate regardless of income tier
        BigDecimal monthlySalary = basicSalary.min(config.getMaxSalaryCap());
        BigDecimal monthlyContribution = monthlySalary.multiply(config.getEmployerRate());
        return monthlyContribution.divide(periodicDivisor(frequency), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal periodicDivisor(PayrollFrequency frequency) {
        return frequency == PayrollFrequency.SEMI_MONTHLY
                ? SEMI_MONTHLY_PERIODS_PER_MONTH
                : BigDecimal.ONE;
    }

    public BigDecimal calculateTotalEmployerContributions(BigDecimal sssEr, BigDecimal philhealthEr, BigDecimal pagibigEr) {
        return sssEr.add(philhealthEr).add(pagibigEr).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateTaxableIncome(BigDecimal grossPay, BigDecimal statutoryDeductions) {
        return grossPay.subtract(statutoryDeductions).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateAbsenceDeduction(BigDecimal dailyRate, BigDecimal absenceDays) {
        return dailyRate.multiply(absenceDays).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateTardinessDeduction(BigDecimal hourlyRate, Integer tardinessMinutes) {
        if (tardinessMinutes == null || tardinessMinutes == 0) {
            return BigDecimal.ZERO;
        }

        return hourlyRate
                .multiply(BigDecimal.valueOf(tardinessMinutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateUndertimeDeduction(BigDecimal hourlyRate, Integer undertimeMinutes) {
        if (undertimeMinutes == null || undertimeMinutes == 0) {
            return BigDecimal.ZERO;
        }

        return hourlyRate
                .multiply(BigDecimal.valueOf(undertimeMinutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateRegularPay(BigDecimal semiMonthlyRate, BigDecimal absenceDeduction, BigDecimal tardinessDeduction, BigDecimal undertimeDeduction) {
        return semiMonthlyRate
                .subtract(absenceDeduction)
                .subtract(tardinessDeduction)
                .subtract(undertimeDeduction)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateNetPay( BigDecimal grossPay, BigDecimal nonTaxableBenefits, BigDecimal statutoryDeductions, BigDecimal withholdingTax) {
        return grossPay.add(nonTaxableBenefits)
                .subtract(statutoryDeductions)
                .subtract(withholdingTax)
                .setScale(2, RoundingMode.HALF_UP);
    }

}