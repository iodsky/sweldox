package com.iodsky.motorph.employee;

import com.iodsky.motorph.common.exception.DuplicateFieldException;
import com.iodsky.motorph.common.exception.NotFoundException;
import com.iodsky.motorph.employee.model.Compensation;
import com.iodsky.motorph.employee.model.Employee;
import com.iodsky.motorph.employee.model.EmploymentDetails;
import com.iodsky.motorph.employee.request.EmployeeRequest;
import com.iodsky.motorph.employee.request.EmploymentDetailsRequest;
import com.iodsky.motorph.organization.Department;
import com.iodsky.motorph.organization.DepartmentService;
import com.iodsky.motorph.organization.Position;
import com.iodsky.motorph.organization.PositionService;
import com.iodsky.motorph.security.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock private EmployeeMapper employeeMapper;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentService departmentService;
    @Mock private PositionService positionService;
    @InjectMocks private EmployeeService employeeService;

    private EmployeeRequest request;
    private Employee employee;
    private Department department;
    private Position position;

    @BeforeEach
    void setUp() {
        department = new Department();
        department.setId("DEP001");

        position = new Position();
        position.setId("POS001");

        employee = new Employee();
        EmploymentDetails details = new EmploymentDetails();
        employee.setEmploymentDetails(details);

        Compensation compensation = new Compensation();
        compensation.setBenefits(new ArrayList<>());
        employee.setCompensation(compensation);

        request = new EmployeeRequest();
        EmploymentDetailsRequest reqDetails = new EmploymentDetailsRequest();
        reqDetails.setDepartmentId("DEP001");
        reqDetails.setPositionId("POS001");
        request.setEmploymentDetails(reqDetails);
    }

    @Nested
    class CreateEmployeeTests {
        @Test
        void shouldCreateEmployeeSuccessfully() {
            when(employeeMapper.toEntity(request)).thenReturn(employee);
            when(departmentService.getDepartmentById("DEP001")).thenReturn(department);
            when(positionService.getPositionById("POS001")).thenReturn(position);
            when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

            Employee result = employeeService.createEmployee(request);

            assertNotNull(result);
            verify(employeeRepository).save(employee);
            assertEquals(department, result.getEmploymentDetails().getDepartment());
        }

        @Test
        void shouldCreateEmployeeWithNullSupervisorWhenNotProvided() {
            when(employeeMapper.toEntity(request)).thenReturn(employee);
            when(departmentService.getDepartmentById("DEP001")).thenReturn(department);
            when(positionService.getPositionById("POS001")).thenReturn(position);
            when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

            request.getEmploymentDetails().setSupervisorId(null);

            Employee result = employeeService.createEmployee(request);

            assertNull(result.getEmploymentDetails().getSupervisor());
        }

        @Test
        void shouldThrowDuplicateFieldExceptionWhenDuplicateKeyDetected() {
            when(employeeMapper.toEntity(request)).thenReturn(employee);
            DataIntegrityViolationException ex = new DataIntegrityViolationException("Duplicate")
            {
                @Override
                public Throwable getRootCause() {
                    return new Throwable("Key (email)=(john@example.com) already exists");
                }
            };

            when(employeeRepository.save(any(Employee.class))).thenThrow(ex);

            assertThrows(DuplicateFieldException.class, () -> employeeService.createEmployee(request));
        }

        @Test
        void shouldThrowDuplicateFieldExceptionWhenRootCauseIsNull() {
            when(employeeMapper.toEntity(request)).thenReturn(employee);
            DataIntegrityViolationException ex = new DataIntegrityViolationException("constraint fail");

            when(employeeRepository.save(any(Employee.class))).thenThrow(ex);

            assertThrows(DuplicateFieldException.class, () -> employeeService.createEmployee(request));
        }
    }

    @Nested
    class GetAllEmployeesTests {
        @Test
        void shouldReturnAllEmployeesWhenNoFiltersProvided() {
            when(employeeRepository.findAll()).thenReturn(List.of(employee));

            List<Employee> result = employeeService.getAllEmployees(null, null, null);

            assertEquals(1, result.size());
            verify(employeeRepository).findAll();
        }

        @Test
        void shouldReturnEmployeesByDepartmentId() {
            when(employeeRepository.findByEmploymentDetails_Department_Id("DEP001"))
                    .thenReturn(List.of(employee));

            List<Employee> result = employeeService.getAllEmployees("DEP001", null, null);

            assertEquals(1, result.size());
        }

        @Test
        void shouldReturnEmployeesBySupervisorId() {
            when(employeeRepository.findByEmploymentDetails_Supervisor_Id(10L))
                    .thenReturn(List.of(employee));

            List<Employee> result = employeeService.getAllEmployees(null, 10L, null);

            assertEquals(1, result.size());
        }

        @Test
        void shouldThrowExceptionForInvalidStatusValue() {
            assertThrows(IllegalArgumentException.class,
                    () -> employeeService.getAllEmployees(null, null, "INVALID_STATUS"));
        }
    }

    @Nested
    class GetAuthenticatedEmployeeTests {
        @Test
        void shouldReturnAuthenticatedEmployeeWhenPrincipalIsUser() {
            User user = new User();
            user.setEmployee(employee);

            Authentication authentication = mock(Authentication.class);
            SecurityContext context = mock(SecurityContext.class);
            when(authentication.getPrincipal()).thenReturn(user);
            when(context.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(context);

            Employee result = employeeService.getAuthenticatedEmployee();

            assertEquals(employee, result);
        }

        @Test
        void shouldThrowNotFoundWhenPrincipalIsNotUser() {
            Authentication authentication = mock(Authentication.class);
            SecurityContext context = mock(SecurityContext.class);
            when(authentication.getPrincipal()).thenReturn("anonymousUser");
            when(context.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(context);

            assertThrows(NotFoundException.class, () -> employeeService.getAuthenticatedEmployee());
        }
    }

    @Nested
    class GetEmployeeByIdTests {
        @Test
        void shouldReturnEmployeeById() {
            when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

            Employee result = employeeService.getEmployeeById(1L);

            assertEquals(employee, result);
        }

        @Test
        void shouldThrowNotFoundWhenEmployeeDoesNotExist() {
            when(employeeRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> employeeService.getEmployeeById(1L));
        }
    }

    @Nested
    class UpdateEmployeeTests {
        @Test
        void shouldUpdateEmployeeSuccessfully() {
            when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
            when(departmentService.getDepartmentById("DEP001")).thenReturn(department);
            when(positionService.getPositionById("POS001")).thenReturn(position);
            when(employeeRepository.save(any(Employee.class))).thenReturn(employee);

            Employee result = employeeService.updateEmployeeById(1L, request);

            assertNotNull(result);
            verify(employeeRepository).save(employee);
        }

        @Test
        void shouldThrowDuplicateFieldExceptionWhenUpdatingWithDuplicate() {
            when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

            DataIntegrityViolationException ex = new DataIntegrityViolationException("Duplicate")
            {
                @Override
                public Throwable getRootCause() {
                    return new Throwable("Key (email)=(john@example.com) already exists");
                }
            };

            when(employeeRepository.save(any(Employee.class))).thenThrow(ex);

            assertThrows(DuplicateFieldException.class, () -> employeeService.updateEmployeeById(1L, request));
        }

        @Test
        void shouldThrowNotFoundWhenUpdatingNonexistentEmployee() {
            when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> employeeService.updateEmployeeById(99L, request));
        }
    }


    @Nested
    class DeleteEmployeeTests {
        @Test
        void shouldDeleteEmployeeSuccessfully() {
            when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

            employeeService.deleteEmployeeById(1L);

            verify(employeeRepository).delete(employee);
        }

        @Test
        void shouldThrowNotFoundWhenDeletingNonexistentEmployee() {
            when(employeeRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> employeeService.deleteEmployeeById(1L));
        }
    }
}
