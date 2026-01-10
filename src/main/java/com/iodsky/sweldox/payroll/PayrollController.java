package com.iodsky.sweldox.payroll;

import com.iodsky.sweldox.common.response.ApiResponse;
import com.iodsky.sweldox.common.response.BatchResponse;
import com.iodsky.sweldox.common.response.PaginationMeta;
import com.iodsky.sweldox.common.response.ResponseFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Payroll", description = "Payroll processing and management endpoints")
public class PayrollController {

    private final PayrollService payrollService;
    private final PayrollMapper payrollMapper;

    @PreAuthorize("hasRole('PAYROLL')")
    @PostMapping
    @Operation(summary = "Create payroll", description = "Generate payroll for a single employee or batch process for all employees. Requires PAYROLL role.")
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
    @Operation(summary = "Get all payroll records", description = "Retrieve all payroll records with pagination and optional date filtering. Requires PAYROLL role.")
    public ResponseEntity<ApiResponse<List<PayrollDto>>> getAllPayroll(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @Parameter(description = "Number of items per page (1-100)") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @Parameter(description = "Filter by period start date") @RequestParam(required = false) LocalDate periodStartDate,
            @Parameter(description = "Filter by period end date") @RequestParam(required = false) LocalDate periodEndDate
    ) {

        Page<Payroll> page = payrollService.getAllPayroll(pageNo, limit, periodStartDate, periodEndDate);

        List<PayrollDto> payroll = page.getContent().stream().map(payrollMapper::toDto).toList();

        return ResponseFactory.ok("Payroll retrieved successfully", payroll, PaginationMeta.of(page));
    }

    @GetMapping("/me")
    @Operation(summary = "Get my payroll records", description = "Retrieve payroll records for the authenticated employee")
    public ResponseEntity<ApiResponse<List<PayrollDto>>> getAllEmployeePayroll(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @Parameter(description = "Number of items per page (1-100)") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @Parameter(description = "Filter by period start date") @RequestParam(required = false) LocalDate periodStartDate,
            @Parameter(description = "Filter by period end date") @RequestParam(required = false) LocalDate periodEndDate
    ) {
        Page<Payroll> page = payrollService.getAllEmployeePayroll(pageNo, limit, periodStartDate, periodEndDate);

        List<PayrollDto> payroll = page.getContent().stream().map(payrollMapper::toDto).toList();

        return ResponseFactory.ok("Payroll retrieved successfully", payroll, PaginationMeta.of(page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payroll by ID", description = "Retrieve a specific payroll record by its ID")
    public ResponseEntity<ApiResponse<PayrollDto>> getPayrollById(@Parameter(description = "Payroll ID") @PathVariable("id") UUID id) {
        PayrollDto dto = payrollMapper.toDto(payrollService.getPayrollById(id));
        return ResponseFactory.ok("Payroll retrieved successfully", dto);
    }
}
