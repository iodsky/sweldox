package com.iodsky.motorph.payroll.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "benefit_type")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BenefitType {

    @Id
    private String id;
    private String type;

}