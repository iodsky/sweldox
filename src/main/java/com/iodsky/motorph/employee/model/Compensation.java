package com.iodsky.motorph.employee.model;

import com.iodsky.motorph.payroll.model.Benefit;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "compensation")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Compensation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "basic_salary")
    private BigDecimal basicSalary;

    @Column(name = "hourly_rate")
    private BigDecimal hourlyRate;

    @Column(name = "semi_monthly_rate")
    private BigDecimal semiMonthlyRate;

    @OneToMany(mappedBy = "compensation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Benefit> benefits;

}
