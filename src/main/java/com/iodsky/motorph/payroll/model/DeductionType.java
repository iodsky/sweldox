package com.iodsky.motorph.payroll.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "deduction_type")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeductionType {
    @Id
    private String code;
    private String type;
}