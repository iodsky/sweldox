package com.iodsky.motorph.payroll;

import com.iodsky.motorph.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BenefitService {

    private final BenefitTypeRepository benefitTypeRepository;

    public BenefitType getBenefitTypeById(String id) {
        return benefitTypeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Benefit type " + id + " not found"));
    }

}
