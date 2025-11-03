package com.iodsky.motorph.payroll.model;

import com.iodsky.motorph.employee.model.Compensation;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "benefit")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Benefit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "compensation_id")
    private Compensation compensation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "benefit_type_id")
    private BenefitType benefitType;

    private BigDecimal amount;

}
