package com.iodsky.motorph.payroll.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "benefit_type")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BenefitType {

    @Id
    private String id;
    private String type;

}