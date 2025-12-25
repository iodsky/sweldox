package com.iodsky.sweldox.employee;

import com.iodsky.sweldox.common.exception.CsvImportException;
import com.iodsky.sweldox.common.exception.DuplicateFieldException;
import com.iodsky.sweldox.csvimport.CsvResult;
import com.iodsky.sweldox.csvimport.CsvService;
import com.iodsky.sweldox.organization.Department;
import com.iodsky.sweldox.organization.DepartmentService;
import com.iodsky.sweldox.organization.Position;
import com.iodsky.sweldox.organization.PositionService;
import com.iodsky.sweldox.payroll.BenefitService;
import com.iodsky.sweldox.payroll.Benefit;
import com.iodsky.sweldox.payroll.BenefitType;
import com.iodsky.sweldox.security.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final EmployeeRepository employeeRepository;
    private final DepartmentService departmentService;
    private final PositionService positionService;
    private final BenefitService benefitService;
    private final CsvService<Employee, EmployeeCsvRecord> employeeCsvService;

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

    public Page<Employee> getAllEmployees(int page, int limit, String departmentId, Long supervisorId, String status) {

        Pageable pageable = PageRequest.of(page, limit);

        if (departmentId != null) {
            return employeeRepository.findByEmploymentDetails_Department_Id(departmentId, pageable);
        } else if (supervisorId != null) {
            return employeeRepository.findByEmploymentDetails_Supervisor_Id(supervisorId, pageable);
        } else if (status != null) {
            return  employeeRepository.findByEmploymentDetails_Status(Status.valueOf(status.toUpperCase()), pageable);
        }

        return employeeRepository.findAll(pageable);
    }

    public Employee getAuthenticatedEmployee() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof User user) {
            return user.getEmployee();
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found");
    }

    public Employee getEmployeeById(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee " + id + " not found"));
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee " + id + " not found");
        });
    }

    public List<Long> getAllActiveEmployeeIds() {
        return employeeRepository.findAllActiveEmployeeIds();
    }

    @Transactional
    public Integer importEmployees(MultipartFile file) {
        try {
            LinkedHashSet<CsvResult<Employee, EmployeeCsvRecord>> records =
                    employeeCsvService.parseCsv(file.getInputStream(), EmployeeCsvRecord.class);

            BenefitType mealBenefitType = benefitService.getBenefitTypeById("MEAL");
            BenefitType clothingBenefitType = benefitService.getBenefitTypeById("CLOTHING");
            BenefitType phoneBenefitType = benefitService.getBenefitTypeById("PHONE");

            Set<String> positionTitles = records.stream()
                    .map(r -> r.source().getPosition())
                    .collect(Collectors.toSet());
            Map<String, Position> positionMap = positionService.getPositionsByTitles(positionTitles);

            Map<Employee, Long> employeeSupervisorMap = new HashMap<>();

            LinkedHashSet<Employee> employees = records.stream().map(r -> {
                Employee employee = r.entity();
                EmployeeCsvRecord csv = r.source();

                Position position = positionMap.get(csv.getPosition());
                employee.getEmploymentDetails().setPosition(position);
                employee.getEmploymentDetails().setDepartment(position.getDepartment());

                Benefit mealAllowance = Benefit.builder()
                        .benefitType(mealBenefitType)
                        .amount(csv.getMealAllowance())
                        .build();

                Benefit clothingAllowance = Benefit.builder()
                        .benefitType(clothingBenefitType)
                        .amount(csv.getClothingAllowance())
                        .build();

                Benefit phoneAllowance = Benefit.builder()
                        .benefitType(phoneBenefitType)
                        .amount(csv.getPhoneAllowance())
                        .build();

                employee.getCompensation().setBenefits(
                        new ArrayList<>(List.of(mealAllowance, phoneAllowance, clothingAllowance))
                );
                employee.getCompensation().getBenefits()
                        .forEach(b -> b.setCompensation(employee.getCompensation()));

                employeeSupervisorMap.put(employee, csv.getSupervisorId());

                return employee;
            }).collect(Collectors.toCollection(LinkedHashSet::new));

            List<Employee> savedEmployees = employeeRepository.saveAll(employees);

            Map<Long, Employee> employeeIdMap = savedEmployees.stream()
                    .collect(Collectors.toMap(Employee::getId, e -> e));

            // Set supervisor relationships
            for (Employee employee : savedEmployees) {
                Long supervisorId = employeeSupervisorMap.get(employee);
                if (supervisorId != null) {
                    Employee supervisor = employeeIdMap.get(supervisorId);

                    if (supervisor == null) {
                        try {
                            supervisor = getEmployeeById(supervisorId);
                        } catch (ResponseStatusException e) {
                            continue;
                        }
                    }

                    employee.getEmploymentDetails().setSupervisor(supervisor);
                }
            }

            employeeRepository.saveAll(savedEmployees);

            return savedEmployees.size();
        } catch (IOException e) {
            throw new CsvImportException(e.getMessage());
        }
    }
}
