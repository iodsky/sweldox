package com.iodsky.sweldox.payroll;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BenefitService {

    private final BenefitTypeRepository benefitTypeRepository;

    public BenefitType getBenefitTypeById(String id) {
        return benefitTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Benefit type " + id + " not found"));
    }

}
