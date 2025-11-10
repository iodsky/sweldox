package com.iodsky.motorph.employee;

import com.iodsky.motorph.employee.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByEmploymentDetails_Status(Status status);

    List<Employee> findByEmploymentDetails_Department_Id(String departmentId);

    List<Employee> findByEmploymentDetails_Supervisor_Id(Long supervisorId);

    @Query("""
        SELECT e.id
        FROM Employee e
        WHERE e.employmentDetails.status NOT IN (
        com.iodsky.motorph.employee.Status.RESIGNED,
         com.iodsky.motorph.employee.Status.TERMINATED
         )
       """)
    List<Long> findAllActiveEmployeeIds();

}
