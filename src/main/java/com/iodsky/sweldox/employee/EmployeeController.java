package com.iodsky.sweldox.employee;

import com.iodsky.sweldox.common.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/employees")
@Validated
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Employee management endpoints")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeMapper employeeMapper;

    @PreAuthorize("hasRole('HR')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create a new employee",
            description = "Create a new employee record. Requires HR role.",
            operationId = "createEmployee"
    )
    public ResponseEntity<ApiResponse<EmployeeDto>> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        EmployeeDto employee = employeeMapper.toDto(employeeService.createEmployee(request));
        return ResponseFactory.created("Employee created successfully", employee);
    }

    @PreAuthorize("hasAnyRole('HR', 'IT')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import employees from CSV",
            description = "Bulk import employees from a CSV file. Requires HR or IT role. Returns the count of imported employees.",
            operationId = "importEmployees"
    )
    public ResponseEntity<ApiResponse<BatchResponse>> importEmployees(@RequestPart("file") MultipartFile file) {
        Integer count = employeeService.importEmployees(file);
        return ResponseFactory.ok("Employees imported successfully", new BatchResponse(count));
    }

    @PreAuthorize("hasAnyRole('HR', 'IT', 'PAYROLL')")
    @GetMapping
    @Operation(summary = "Get all employees", description = "Retrieve a paginated list of employees with optional filters. Requires HR, IT, or PAYROLL role.")
    public ResponseEntity<ApiResponse<List<EmployeeDto>>> getAllEmployees(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @Parameter(description = "Number of items per page (1-100)") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @Parameter(description = "Filter by department") @RequestParam(required = false) String department,
            @Parameter(description = "Filter by supervisor ID") @RequestParam(required = false) @Positive Long supervisor,
            @Parameter(description = "Filter by employment status") @RequestParam(required = false) String status
    ) {
        Page<Employee> page = employeeService.getAllEmployees(pageNo, limit, department, supervisor, status);

        List<EmployeeDto> employees = page.getContent().stream().map(employeeMapper::toDto).toList();

        return ResponseFactory.ok(
                "Employees retrieved successfully",
                employees,
                PaginationMeta.of(page)
        );

    }

    @GetMapping("/me")
    @Operation(summary = "Get current employee", description = "Retrieve the authenticated employee's information")
    public ResponseEntity<ApiResponse<EmployeeDto>> getAuthenticatedEmployee() {
        EmployeeDto employee =  employeeMapper.toDto(employeeService.getAuthenticatedEmployee());
        return ResponseFactory.ok("Employee retrieved successfully", employee);
    }

    @PreAuthorize("hasAnyRole('HR', 'IT', 'PAYROLL')")
    @GetMapping("/{id}")
    @Operation(summary = "Get employee by ID", description = "Retrieve a specific employee by their ID. Requires HR, IT, or PAYROLL role.")
    public ResponseEntity<ApiResponse<EmployeeDto>> getEmployeeById(@Parameter(description = "Employee ID") @PathVariable long id) {
        EmployeeDto employee = employeeMapper.toDto(employeeService.getEmployeeById(id));
        return ResponseFactory.ok("Employee retrieved successfully", employee);
    }

    @PreAuthorize("hasRole('HR')")
    @PutMapping("/{id}")
    @Operation(summary = "Update employee", description = "Update an existing employee's information. Requires HR role.")
    public ResponseEntity<ApiResponse<EmployeeDto>> updateEmployee(@Parameter(description = "Employee ID") @PathVariable long id, @Valid @RequestBody EmployeeRequest request) {
        EmployeeDto employee = employeeMapper.toDto(employeeService.updateEmployeeById(id, request));
        return ResponseFactory.ok("Employee updated successfully", employee);
    }

    @PreAuthorize("hasRole('HR')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete employee", description = "Delete or deactivate an employee. Requires HR role.")
    public ResponseEntity<ApiResponse<DeleteResponse>> deleteEmployee(@Parameter(description = "Employee ID") @PathVariable long id, @Parameter(description = "Status to set (INACTIVE or TERMINATED)") @RequestParam String status) {
        employeeService.deleteEmployeeById(id, status);
        DeleteResponse res = new DeleteResponse("Employee", id);
        return ResponseFactory.ok("Employee deleted successfully", res);
    }

}
