package com.iodsky.motorph.payroll;

import com.iodsky.motorph.payroll.model.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, UUID> {

    List<Payroll> findAllByPeriodStartDateBetween(LocalDate startDate, LocalDate endDate);

    List<Payroll> findAllByEmployee_IdAndPeriodStartDateBetween(Long employeeId, LocalDate startDate, LocalDate endDate);
}
