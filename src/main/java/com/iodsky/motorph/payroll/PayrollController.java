package com.iodsky.motorph.payroll;

import com.iodsky.motorph.common.ApiResponse;
import com.iodsky.motorph.common.BatchResponse;
import com.iodsky.motorph.common.ResponseFactory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;
    private final PayrollMapper payrollMapper;

    @PreAuthorize("hasRole('PAYROLL')")
    @PostMapping
    public ResponseEntity<ApiResponse<Object>> createPayroll(
            @RequestBody PayrollRequest request)  {

        if (request.getEmployeeId() == null) {
            Integer created = payrollService.createPayrollBatch(
                    request.getPeriodStartDate(),
                    request.getPeriodEndDate(),
                    request.getPayDate());

            return ResponseFactory.ok("Batch payroll created successfully", new BatchResponse(created));
        }

        PayrollDto payroll = payrollMapper.toDto(payrollService.createPayroll(
                request.getEmployeeId(),
                request.getPeriodStartDate(),
                request.getPeriodEndDate(),
                request.getPayDate()));

        return ResponseFactory.created("Payroll created successfully", payroll);
    }

    @PreAuthorize("hasRole('PAYROLL')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PayrollDto>>> getAllPayroll(
            @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) LocalDate periodStartDate,
            @RequestParam(required = false) LocalDate periodEndDate
    ) {

        Page<Payroll> page = payrollService.getAllPayroll(pageNo, limit, periodStartDate, periodEndDate);

        List<PayrollDto> payroll = page.getContent().stream().map(payrollMapper::toDto).toList();

        return ResponseFactory.ok("Payroll retrieved successfully", payroll);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<PayrollDto>>> getAllEmployeePayroll(
            @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) LocalDate periodStartDate,
            @RequestParam(required = false) LocalDate periodEndDate
    ) {
        Page<Payroll> page = payrollService.getAllEmployeePayroll(pageNo, limit, periodStartDate, periodEndDate);

        List<PayrollDto> payroll = page.getContent().stream().map(payrollMapper::toDto).toList();

        return ResponseFactory.ok("Payroll retrieved successfully", payroll);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PayrollDto>> getPayrollById(@PathVariable("id") UUID id) {
        PayrollDto dto = payrollMapper.toDto(payrollService.getPayrollById(id));
        return ResponseFactory.ok("Payroll retrieved successfully", dto);
    }
}
