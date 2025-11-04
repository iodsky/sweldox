package com.iodsky.motorph.payroll;

import com.iodsky.motorph.payroll.model.BenefitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BenefitTypeRepository extends JpaRepository<BenefitType, String> {
}
