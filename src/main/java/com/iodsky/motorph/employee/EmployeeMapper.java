package com.iodsky.motorph.employee;

import com.iodsky.motorph.employee.model.Compensation;
import com.iodsky.motorph.employee.model.Employee;
import com.iodsky.motorph.employee.model.EmploymentDetails;
import com.iodsky.motorph.employee.model.GovernmentId;
import com.iodsky.motorph.employee.request.EmployeeRequest;
import com.iodsky.motorph.payroll.BenefitDto;
import com.iodsky.motorph.payroll.BenefitMapper;
import com.iodsky.motorph.payroll.model.Benefit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EmployeeMapper {

    private final BenefitMapper benefitMapper;

    public EmployeeDto toDto(Employee employee) {

        Employee supervisor = employee.getEmploymentDetails().getSupervisor();
        String supervisorName = supervisor != null ? supervisor.getFirstName() + " " + supervisor.getLastName() : "N/A";

        List<BenefitDto> benefits = employee.getCompensation().getBenefits()
                .stream()
                .map(benefitMapper::toDto)
                .toList();
        return EmployeeDto.builder()
                .id(employee.getId())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .birthday(employee.getBirthday())
                .address(employee.getAddress())
                .phoneNumber(employee.getPhoneNumber())
                .sssNumber(employee.getGovernmentId().getSssNumber())
                .tinNumber(employee.getGovernmentId().getTinNumber())
                .philhealthNumber(employee.getGovernmentId().getPhilhealthNumber())
                .pagIbigNumber(employee.getGovernmentId().getPagIbigNumber())
                .status(employee.getEmploymentDetails().getStatus().toString())
                .supervisor(supervisorName)
                .department(employee.getEmploymentDetails().getDepartment().getTitle())
                .position(employee.getEmploymentDetails().getPosition().getTitle())
                .basicSalary(employee.getCompensation().getBasicSalary())
                .hourlyRate(employee.getCompensation().getHourlyRate())
                .semiMonthlyRate(employee.getCompensation().getSemiMonthlyRate())
                .benefits(benefits)
                .build();
    }

    public Employee toEntity(EmployeeRequest request) {
        Employee employee = Employee.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .birthday(request.getBirthday())
                .address(request.getAddress())
                .phoneNumber(request.getPhoneNumber())
                .build();

        GovernmentId governmentId = GovernmentId.builder()
                .employee(employee)
                .sssNumber(request.getGovernmentId().getSssNumber())
                .tinNumber(request.getGovernmentId().getTinNumber())
                .philhealthNumber(request.getGovernmentId().getPhilhealthNumber())
                .pagIbigNumber(request.getGovernmentId().getPagIbigNumber())
                .build();

        EmploymentDetails employmentDetails = EmploymentDetails.builder()
                .employee(employee)
                .status(request.getEmploymentDetails().getStatus())
                .build();

        BigDecimal basicSalary = request.getCompensation().getBasicSalary();
        BigDecimal semiMonthlyRate = basicSalary.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal hourlyRate = basicSalary.divide(BigDecimal.valueOf(21.75).multiply(BigDecimal.valueOf(8)), 2, RoundingMode.HALF_UP);

        Compensation compensation = Compensation.builder()
                .employee(employee)
                .basicSalary(basicSalary)
                .semiMonthlyRate(semiMonthlyRate)
                .hourlyRate(hourlyRate)
                .build();

        List<Benefit> benefits = request.getCompensation().getBenefits()
                        .stream()
                        .map(benefitMapper::toEntity)
                        .toList();

        benefits.forEach(b -> b.setCompensation(compensation));
        compensation.setBenefits(benefits);

        employee.setGovernmentId(governmentId);
        employee.setEmploymentDetails(employmentDetails);
        employee.setCompensation(compensation);

        return employee;
    }

    public void updateEntity(Employee existing, EmployeeRequest request) {

        // --- BASIC INFO ---
        existing.setFirstName(request.getFirstName());
        existing.setLastName(request.getLastName());
        existing.setBirthday(request.getBirthday());
        existing.setAddress(request.getAddress());
        existing.setPhoneNumber(request.getPhoneNumber());

        // --- GOVERNMENT IDs ---
        if (existing.getGovernmentId() == null) {
            existing.setGovernmentId(new GovernmentId());
            existing.getGovernmentId().setEmployee(existing);
        }
        GovernmentId gov = existing.getGovernmentId();
        gov.setSssNumber(request.getGovernmentId().getSssNumber());
        gov.setTinNumber(request.getGovernmentId().getTinNumber());
        gov.setPhilhealthNumber(request.getGovernmentId().getPhilhealthNumber());
        gov.setPagIbigNumber(request.getGovernmentId().getPagIbigNumber());

        // --- EMPLOYMENT DETAILS ---
        if (existing.getEmploymentDetails() == null) {
            existing.setEmploymentDetails(new EmploymentDetails());
            existing.getEmploymentDetails().setEmployee(existing);
        }
        EmploymentDetails details = existing.getEmploymentDetails();
        details.setStatus(request.getEmploymentDetails().getStatus());

        // --- COMPENSATION ---
        if (existing.getCompensation() == null) {
            existing.setCompensation(new Compensation());
            existing.getCompensation().setEmployee(existing);
        }

        BigDecimal basicSalary = request.getCompensation().getBasicSalary();
        BigDecimal semiMonthlyRate = basicSalary.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal hourlyRate = basicSalary.divide(BigDecimal.valueOf(21.75).multiply(BigDecimal.valueOf(8)), 2, RoundingMode.HALF_UP);

        Compensation comp = existing.getCompensation();
        comp.setBasicSalary(basicSalary);
        comp.setSemiMonthlyRate(semiMonthlyRate);
        comp.setHourlyRate(hourlyRate);

        List<Benefit> benefits = request.getCompensation().getBenefits().stream()
                .map(benefitMapper::toEntity)
                .toList();
        benefits.forEach(b -> b.setCompensation(comp));
        comp.getBenefits().clear();
        comp.getBenefits().addAll(benefits);
    }
}
