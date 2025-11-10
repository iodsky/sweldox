package com.iodsky.motorph.payroll.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iodsky.motorph.employee.model.Compensation;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "benefit")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Benefit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "compensation_id")
    @JsonIgnore
    private Compensation compensation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "benefit_type_id")
    @JsonIgnore
    private BenefitType benefitType;

    private BigDecimal amount;

}
