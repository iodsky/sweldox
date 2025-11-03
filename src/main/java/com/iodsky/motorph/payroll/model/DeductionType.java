package com.iodsky.motorph.payroll.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "deduction_type")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeductionType {
    @Id
    private String code;
    private String type;
}