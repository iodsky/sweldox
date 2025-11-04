package com.iodsky.motorph.employee;

import com.iodsky.motorph.common.exception.DuplicateFieldException;
import com.iodsky.motorph.employee.model.Employee;
import com.iodsky.motorph.employee.request.EmployeeRequest;
import com.iodsky.motorph.common.exception.NotFoundException;
import com.iodsky.motorph.organization.Department;
import com.iodsky.motorph.organization.DepartmentService;
import com.iodsky.motorph.organization.Position;
import com.iodsky.motorph.organization.PositionService;
import com.iodsky.motorph.payroll.BenefitService;
import com.iodsky.motorph.payroll.model.Benefit;
import com.iodsky.motorph.security.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final EmployeeRepository employeeRepository;
    private final DepartmentService departmentService;
    private final PositionService positionService;
    private final BenefitService benefitService;

    public Employee createEmployee(EmployeeRequest request) {
        try {
            Employee employee = employeeMapper.toEntity(request);

            Employee supervisor = null;
            if (request.getEmploymentDetails().getSupervisorId() != null) {
                supervisor = getEmployeeById(request.getEmploymentDetails().getSupervisorId());
            }

            Department department = departmentService.getDepartmentById(request.getEmploymentDetails().getDepartmentId());
            Position position = positionService.getPositionById(request.getEmploymentDetails().getPositionId());

            employee.getEmploymentDetails().setSupervisor(supervisor);
            employee.getEmploymentDetails().setDepartment(department);
            employee.getEmploymentDetails().setPosition(position);

            List<Benefit> benefits = employee.getCompensation().getBenefits();
            benefits.forEach(b -> {
                b.setBenefitType(benefitService.getBenefitTypeById(b.getBenefitType().getId()));
            });

            return employeeRepository.save(employee);
        } catch (DataIntegrityViolationException e) {
            String msg = null;

            if (e.getRootCause() != null) {
                msg = e.getRootCause().getMessage();
            }

            if (msg != null && msg.contains("Key (")) {
                msg = msg.split("[()]")[1];
                throw new DuplicateFieldException("Duplicate value for field '" + msg + "'");
            }
            throw new DuplicateFieldException("Duplicate field detected");
        }
    }

    public List<Employee> getAllEmployees(String departmentId, Long supervisorId, String status) {

        if (departmentId != null) {
            return employeeRepository.findByEmploymentDetails_Department_Id(departmentId);
        } else if (supervisorId != null) {
            return employeeRepository.findByEmploymentDetails_Supervisor_Id(supervisorId);
        } else if (status != null) {
            return  employeeRepository.findByEmploymentDetails_Status(Status.valueOf(status.toUpperCase()));
        }

        return employeeRepository.findAll();
    }

    public Employee getAuthenticatedEmployee() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof User user) {
            return user.getEmployee();
        }

        throw new NotFoundException("Authenticated user not found");
    }

    public Employee getEmployeeById(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
    }

    public Employee updateEmployeeById(Long id, EmployeeRequest request) {
        Employee employee = this.getEmployeeById(id);

        try {
            Employee supervisor = null;
            if (request.getEmploymentDetails().getSupervisorId() != null) {
                supervisor = getEmployeeById(request.getEmploymentDetails().getSupervisorId());
            }

            Department department = departmentService.getDepartmentById(request.getEmploymentDetails().getDepartmentId());
            Position position = positionService.getPositionById(request.getEmploymentDetails().getPositionId());

            employee.getEmploymentDetails().setSupervisor(supervisor);
            employee.getEmploymentDetails().setDepartment(department);
            employee.getEmploymentDetails().setPosition(position);

            employeeMapper.updateEntity(employee, request);

            return employeeRepository.save(employee);

        } catch (DataIntegrityViolationException e) {
            String msg = e.getRootCause() != null ? e.getRootCause().getMessage() : e.getMessage();
            if (msg != null && msg.contains("Key (")) {
                msg = msg.split("[()]")[1];
                throw new DuplicateFieldException("Duplicate value for field '" + msg + "'");
            }
            throw new DuplicateFieldException("Duplicate field detected");
        }
    }

    public void deleteEmployeeById(Long id) {
        employeeRepository.findById(id).ifPresentOrElse(employeeRepository::delete, () -> {
            throw new NotFoundException("Employee " + id + " not found");
        });
    }

}
