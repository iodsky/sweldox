package com.iodsky.motorph.payroll;

import com.iodsky.motorph.payroll.model.Benefit;
import com.iodsky.motorph.payroll.model.BenefitType;
import org.springframework.stereotype.Component;

@Component
public class BenefitMapper {

    public BenefitDto toDto(Benefit benefit) {
        if (benefit == null) {
            return null;
        }

        return BenefitDto.builder()
                .benefit(benefit.getBenefitType().getType())
                .amount(benefit.getAmount())
                .build();
    }

    public Benefit toEntity(BenefitDto benefitDto) {
        if (benefitDto == null) {
            return  null;
        }

        return Benefit.builder()
                .benefitType(BenefitType.builder().id(benefitDto.getBenefit()).build())
                .amount(benefitDto.getAmount())
                .build();
    }

}
