package com.iodsky.motorph.payroll.model;

import com.iodsky.motorph.employee.model.Employee;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payroll")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @OneToMany(mappedBy = "payroll", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Deduction> deductions;

    @Column(name = "period_start_date")
    private LocalDate periodStartDate;

    @Column(name = "period_end_date")
    private LocalDate periodEndDate;

    @Column(name = "pay_date")
    private LocalDate payDate;

    @Column(name = "days_worked")
    private int daysWorked;

    private BigDecimal overtime;

    @Column(name = "monthly_rate")
    private BigDecimal monthlyRate;

    @Column(name = "daily_rate")
    private BigDecimal dailyRate;

    @Column(name = "gross_pay")
    private BigDecimal grossPay;

    @Column(name = "total_benefits")
    private BigDecimal totalBenefits;

    @Column(name = "total_deductions")
    private BigDecimal totalDeductions;

    @Column(name = "net_pay")
    private BigDecimal netPay;

}
