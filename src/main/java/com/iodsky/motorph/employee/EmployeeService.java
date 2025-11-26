package com.iodsky.motorph.employee;

import com.iodsky.motorph.common.exception.CsvImportException;
import com.iodsky.motorph.common.exception.DuplicateFieldException;
import com.iodsky.motorph.csvimport.CsvResult;
import com.iodsky.motorph.csvimport.CsvService;
import com.iodsky.motorph.employee.model.Compensation;
import com.iodsky.motorph.employee.model.Employee;
import com.iodsky.motorph.employee.model.EmploymentDetails;
import com.iodsky.motorph.employee.model.GovernmentId;
import com.iodsky.motorph.employee.request.EmployeeRequest;
import com.iodsky.motorph.common.exception.NotFoundException;
import com.iodsky.motorph.organization.Department;
import com.iodsky.motorph.organization.DepartmentService;
import com.iodsky.motorph.organization.Position;
import com.iodsky.motorph.organization.PositionService;
import com.iodsky.motorph.payroll.BenefitService;
import com.iodsky.motorph.payroll.model.Benefit;
import com.iodsky.motorph.payroll.model.BenefitType;
import com.iodsky.motorph.security.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    public List<Long> getAllActiveEmployeeIds() {
        return employeeRepository.findAllActiveEmployeeIds();
    }

    @Transactional
    public Integer importEmployees(MultipartFile file) {
        try {
            List<CsvResult<Employee, EmployeeCsvRecord>> records =
                    employeeCsvService.parseCsv(file.getInputStream(), EmployeeCsvRecord.class);

            BenefitType mealBenefitType = benefitService.getBenefitTypeById("MEAL");
            BenefitType clothingBenefitType = benefitService.getBenefitTypeById("CLOTHING");
            BenefitType phoneBenefitType = benefitService.getBenefitTypeById("PHONE");

            Set<String> positionTitles = records.stream()
                    .map(r -> r.source().getPosition())
                    .collect(Collectors.toSet());
            Map<String, Position> positionMap = positionService.getPositionsByTitles(positionTitles);

            Map<Employee, Long> employeeSupervisorMap = new HashMap<>();

            List<Employee> employees = records.stream().map(r -> {
                Employee employee = r.entity();
                EmployeeCsvRecord csv = r.source();

                GovernmentId governmentId = GovernmentId.builder()
                        .employee(employee)
                        .sssNumber(csv.getSssId())
                        .philhealthNumber(csv.getPhilhealthId())
                        .tinNumber(csv.getTinId())
                        .pagIbigNumber(csv.getPagibigId())
                        .build();
                employee.setGovernmentId(governmentId);

                Position position = positionMap.get(csv.getPosition());
                EmploymentDetails employmentDetails = EmploymentDetails.builder()
                        .employee(employee)
                        .status(Status.valueOf(csv.getStatus().toUpperCase()))
                        .position(position)
                        .department(position.getDepartment())
                        .build();
                employee.setEmploymentDetails(employmentDetails);

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

                Compensation compensation = Compensation.builder()
                        .employee(employee)
                        .basicSalary(csv.getBasicSalary())
                        .semiMonthlyRate(csv.getSemiMonthlyRate())
                        .hourlyRate(csv.getHourlyRate())
                        .benefits(new ArrayList<>(List.of(mealAllowance, phoneAllowance, clothingAllowance)))
                        .build();
                employee.setCompensation(compensation);
                compensation.getBenefits().forEach(b -> b.setCompensation(compensation));

                employeeSupervisorMap.put(employee, csv.getSupervisorId());

                return employee;
            }).toList();

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
                        } catch (NotFoundException e) {
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
