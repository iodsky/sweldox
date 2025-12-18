package com.iodsky.motorph.employee;

import com.iodsky.motorph.common.*;
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
import java.util.Map;

@RestController
@RequestMapping("/employees")
@Validated
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeMapper employeeMapper;

    @PreAuthorize("hasRole('HR')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<EmployeeDto>> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        EmployeeDto employee = employeeMapper.toDto(employeeService.createEmployee(request));
        return ResponseFactory.created("Employee created successfully", employee);
    }

    @PreAuthorize("hasAnyRole('HR', 'IT')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BatchResponse>> importEmployees(@RequestPart("file") MultipartFile file) {
        Integer count = employeeService.importEmployees(file);
        return ResponseFactory.ok("Employees imported successfully", new BatchResponse(count));
    }

    @PreAuthorize("hasAnyRole('HR', 'IT', 'PAYROLL')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<EmployeeDto>>> getAllEmployees(
            @RequestParam(defaultValue = "0") @Min(0) int pageNo,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @Positive Long supervisor,
            @RequestParam(required = false) String status
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
    public ResponseEntity<ApiResponse<EmployeeDto>> getAuthenticatedEmployee() {
        EmployeeDto employee =  employeeMapper.toDto(employeeService.getAuthenticatedEmployee());
        return ResponseFactory.ok("Employee retrieved successfully", employee);
    }

    @PreAuthorize("hasRole('HR')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeDto>> getEmployeeById(@PathVariable long id) {
        EmployeeDto employee = employeeMapper.toDto(employeeService.getEmployeeById(id));
        return ResponseFactory.ok("Employee retrieved successfully", employee);
    }

    @PreAuthorize("hasRole('HR')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeDto>> updateEmployee(@PathVariable long id, @Valid @RequestBody EmployeeRequest request) {
        EmployeeDto employee = employeeMapper.toDto(employeeService.updateEmployeeById(id, request));
        return ResponseFactory.ok("Employee updated successfully", employee);
    }

    @PreAuthorize("hasRole('HR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<DeleteResponse>> deleteEmployee(@PathVariable long id) {
        employeeService.deleteEmployeeById(id);
        DeleteResponse res = new DeleteResponse("Employee", id);
        return ResponseFactory.ok("Employee deleted successfully", res);
    }

}
