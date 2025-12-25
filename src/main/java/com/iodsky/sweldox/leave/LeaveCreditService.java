package com.iodsky.sweldox.leave;

import com.iodsky.sweldox.common.exception.CsvImportException;
import com.iodsky.sweldox.csvimport.CsvResult;
import com.iodsky.sweldox.csvimport.CsvService;
import com.iodsky.sweldox.employee.EmployeeService;
import com.iodsky.sweldox.employee.Employee;
import com.iodsky.sweldox.security.user.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveCreditService {

    private final LeaveCreditRepository leaveCreditRepository;
    private final EmployeeService employeeService;
    private final CsvService<LeaveCredit, LeaveCreditCsvRecord> leaveCreditService;

    private static final double DEFAULT_VACATION_CREDITS = 14.0;
    private static final double DEFAULT_SICK_CREDITS = 7.0;
    private static final double DEFAULT_BEREAVEMENT_CREDITS = 5.0;

    @Transactional
    public List<LeaveCredit> initializeEmployeeLeaveCredits(InitializeEmployeeLeaveCreditsDto dto) {
        Employee employee = employeeService.getEmployeeById(dto.getEmployeeId());

        String fiscalYear = dto.getFiscalYear();
        if (fiscalYear == null || fiscalYear.isBlank()) {
            int currentYear = LocalDate.now().getYear();
            fiscalYear = currentYear + "-" + (currentYear + 1);
        }

        final String finalFiscalYear = fiscalYear;
        boolean exists = leaveCreditRepository.existsByEmployee_IdAndFiscalYear(employee.getId(), finalFiscalYear);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Leave credits already exists for employee " + employee.getId());
        }

        List<LeaveCredit> leaveCredits = List.of(
                LeaveCredit.builder()
                        .employee(employee)
                        .type(LeaveType.VACATION)
                        .fiscalYear(finalFiscalYear)
                        .credits(DEFAULT_VACATION_CREDITS)
                        .build(),
                LeaveCredit.builder()
                        .employee(employee)
                        .type(LeaveType.SICK)
                        .fiscalYear(finalFiscalYear)
                        .credits(DEFAULT_SICK_CREDITS)
                        .build(),
                LeaveCredit.builder()
                        .employee(employee)
                        .type(LeaveType.BEREAVEMENT)
                        .fiscalYear(finalFiscalYear)
                        .credits(DEFAULT_BEREAVEMENT_CREDITS)
                        .build()
        );

        return leaveCreditRepository.saveAll(leaveCredits);
    }

    public LeaveCredit getLeaveCreditByEmployeeIdAndType(Long employeeId, LeaveType type) {
        return leaveCreditRepository.findByEmployee_IdAndType(employeeId, type)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No " + type + " leave credits found for employeeId: " + employeeId));
    }

    public List<LeaveCredit> getLeaveCreditsByEmployeeId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof User user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to access this resource");
        }

        Long employeeId = user.getEmployee().getId();
        return leaveCreditRepository.findAllByEmployee_Id(employeeId);
    }

    public LeaveCredit updateLeaveCredit (UUID targetId, LeaveCredit updated) {
        LeaveCredit existing = leaveCreditRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave credit not found: " + targetId));

        existing.setCredits(updated.getCredits());

        return leaveCreditRepository.save(existing);
    }

    public void deleteLeaveCreditsByEmployeeId(Long employeeId) {
        List<LeaveCredit> credits = leaveCreditRepository.findAllByEmployee_Id(employeeId);
        leaveCreditRepository.deleteAll(credits);
    }

    public Integer importLeaveCredits(MultipartFile file) {

        try {
            LinkedHashSet<CsvResult<LeaveCredit, LeaveCreditCsvRecord>> csvResults =
                    leaveCreditService.parseCsv(file.getInputStream(), LeaveCreditCsvRecord.class);

            LinkedHashSet<LeaveCredit> leaveCredits = csvResults.stream().map(r -> {
                LeaveCredit entity = r.entity();
                LeaveCreditCsvRecord csv = r.source();

                // Resolve Employee
                Employee employee = employeeService.getEmployeeById(csv.getEmployeeId());
                entity.setEmployee(employee);

                // Resolve leave type
                LeaveType type;
                try {
                    type = LeaveType.valueOf(csv.getType().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid leave type: " + csv.getType());
                }

                entity.setType(type);

                return entity;
            }).collect(Collectors.toCollection(LinkedHashSet::new));

            leaveCreditRepository.saveAll(leaveCredits);

            return leaveCredits.size();
        } catch (IOException e) {
            throw new CsvImportException(e.getMessage());
        }
    }

}
