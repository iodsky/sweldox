package com.iodsky.motorph.payroll;

import com.iodsky.motorph.payroll.model.DeductionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeductionTypeRepository extends JpaRepository<DeductionType, String> {
    Optional<DeductionType> findByCode(String code);
}
