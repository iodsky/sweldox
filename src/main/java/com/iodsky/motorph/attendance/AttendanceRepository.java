package com.iodsky.motorph.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    Optional<Attendance> findByEmployee_IdAndDate(Long employeeId, LocalDate date);

    List<Attendance> findAllByDateBetween(LocalDate startDate, LocalDate endDate);

    List<Attendance> findByEmployee_IdAndDateBetween(Long employeeId, LocalDate startDate, LocalDate endDate);
}