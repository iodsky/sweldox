package com.iodsky.motorph.payroll;

import com.iodsky.motorph.common.exception.NotFoundException;
import com.iodsky.motorph.payroll.model.BenefitType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BenefitService {

    private final BenefitTypeRepository benefitTypeRepository;

    public BenefitType getBenefitTypeById(String id) {
        return benefitTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Benefit type " + id + " not found"));
    }

}
