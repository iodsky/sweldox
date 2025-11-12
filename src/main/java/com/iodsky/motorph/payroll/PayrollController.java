package com.iodsky.motorph.payroll;

import com.iodsky.motorph.payroll.model.Payroll;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;
    private final PayrollMapper payrollMapper;

    @PreAuthorize("hasRole('PAYROLL')")
    @PostMapping
    public ResponseEntity<?> createPayroll(
            @RequestBody PayrollRequest request)  {

        if (request.getEmployeeId() == null) {
            Integer created = payrollService.createPayrollBatch(
                    request.getPeriodStartDate(),
                    request.getPeriodEndDate(),
                    request.getPayDate());
            return new ResponseEntity<>(Map.of("recordsCreated", created), HttpStatus.CREATED);
        }

        Payroll payroll = payrollService.createPayroll(
                request.getEmployeeId(),
                request.getPeriodStartDate(),
                request.getPeriodEndDate(),
                request.getPayDate());
        return new ResponseEntity<>(payrollMapper.toDto(payroll), HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('PAYROLL')")
    @GetMapping
    public ResponseEntity<List<PayrollDto>> getAllPayroll(
            @RequestParam(required = false) LocalDate periodStartDate,
            @RequestParam(required = false) LocalDate periodEndDate) {

        List<PayrollDto> payroll = payrollService.getAllPayroll(periodStartDate, periodEndDate)
                .stream().map(payrollMapper::toDto).toList();
        return ResponseEntity.ok(payroll);
    }

    @GetMapping("/me")
    public ResponseEntity<List<PayrollDto>> getAllEmployeePayroll(
            @RequestParam(required = false) LocalDate periodStartDate,
            @RequestParam(required = false) LocalDate periodEndDate
    ) {
        List<PayrollDto> payroll = payrollService.getAllEmployeePayroll(periodStartDate, periodEndDate)
                .stream().map(payrollMapper::toDto).toList();
        return ResponseEntity.ok(payroll);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PayrollDto> getPayrollById(@PathVariable("id") UUID id) {
        Payroll payroll = payrollService.getPayrollById(id);
        return ResponseEntity.ok(payrollMapper.toDto(payroll));
    }
}
