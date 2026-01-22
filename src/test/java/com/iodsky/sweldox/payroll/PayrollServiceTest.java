package com.iodsky.sweldox.payroll;

import com.iodsky.sweldox.attendance.Attendance;
import com.iodsky.sweldox.attendance.AttendanceService;
import com.iodsky.sweldox.common.DateRange;
import com.iodsky.sweldox.common.DateRangeResolver;
import com.iodsky.sweldox.employee.EmployeeService;
import com.iodsky.sweldox.employee.Employee;
import com.iodsky.sweldox.security.user.User;
import com.iodsky.sweldox.security.user.UserRole;
import com.iodsky.sweldox.security.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock private PayrollRepository payrollRepository;
    @Mock private EmployeeService employeeService;
    @Mock private AttendanceService attendanceService;
    @Mock private UserService userService;
    @Mock private DeductionTypeRepository deductionTypeRepository;
    @Mock private DateRangeResolver dateRangeResolver;
    @InjectMocks private PayrollService payrollService;

    private User payrollUser;
    private User hrUser;
    private User normalUser;
    private Employee employee;
    private Employee otherEmployee;
    private Payroll payroll;
    private DeductionType sssType;
    private DeductionType phicType;
    private DeductionType hdmfType;
    private DeductionType taxType;
    private BenefitType riceAllowanceType;
    private Benefit riceBenefit;

    private static final LocalDate PERIOD_START = LocalDate.of(2025, 11, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2025, 11, 15);
    private static final LocalDate PAY_DATE = LocalDate.of(2025, 11, 20);
    private static final BigDecimal BASIC_SALARY = new BigDecimal("30000.00");
    private static final BigDecimal SEMI_MONTHLY_RATE = new BigDecimal("30000.00");
    private static final BigDecimal HOURLY_RATE = new BigDecimal("178.57");

    @BeforeEach
    void setUp() {
        // Setup employees
        employee = new Employee();
        employee.setId(1L);
        employee.setFirstName("Juan");
        employee.setLastName("Dela Cruz");

        otherEmployee = new Employee();
        otherEmployee.setId(2L);
        otherEmployee.setFirstName("Maria");
        otherEmployee.setLastName("Santos");

        // Setup compensation
        riceAllowanceType = BenefitType.builder()
                .id("RICE")
                .type("Rice Allowance")
                .build();

        riceBenefit = Benefit.builder()
                .id(UUID.randomUUID())
                .benefitType(riceAllowanceType)
                .amount(new BigDecimal("2000.00"))
                .build();

        employee.setBasicSalary(BASIC_SALARY);
        employee.setHourlyRate(HOURLY_RATE);
        employee.setSemiMonthlyRate(SEMI_MONTHLY_RATE);
        employee.setBenefits(List.of(riceBenefit));

        // Setup deduction types
        sssType = DeductionType.builder()
                .code("SSS")
                .type("SSS Contribution")
                .build();

        phicType = DeductionType.builder()
                .code("PHIC")
                .type("PhilHealth Contribution")
                .build();

        hdmfType = DeductionType.builder()
                .code("HDMF")
                .type("Pag-IBIG Contribution")
                .build();

        taxType = DeductionType.builder()
                .code("TAX")
                .type("Withholding Tax")
                .build();

        // Setup users
        payrollUser = new User();
        payrollUser.setUserRole(new UserRole("PAYROLL"));
        payrollUser.setEmployee(employee);

        hrUser = new User();
        hrUser.setUserRole(new UserRole("HR"));
        hrUser.setEmployee(employee);

        normalUser = new User();
        normalUser.setUserRole(new UserRole("EMPLOYEE"));
        normalUser.setEmployee(employee);

        // Setup payroll
        payroll = Payroll.builder()
                .id(UUID.randomUUID())
                .employee(employee)
                .periodStartDate(PERIOD_START)
                .periodEndDate(PERIOD_END)
                .payDate(PAY_DATE)
                .monthlyRate(BASIC_SALARY)
                .dailyRate(new BigDecimal("1428.57"))
                .daysWorked(10)
                .overtime(BigDecimal.ZERO)
                .grossPay(new BigDecimal("14285.70"))
                .totalBenefits(new BigDecimal("2000.00"))
                .totalDeductions(new BigDecimal("2500.00"))
                .netPay(new BigDecimal("13785.70"))
                .deductions(new ArrayList<>())
                .benefits(new ArrayList<>())
                .build();

        SecurityContextHolder.clearContext();
    }

    private void mockAuth(User user) {
        Authentication auth = mock(Authentication.class);
        SecurityContext context = mock(SecurityContext.class);
        when(auth.getPrincipal()).thenReturn(user);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
    }

    private List<Attendance> createMockAttendances(int days) {
        List<Attendance> attendances = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            attendances.add(Attendance.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .date(PERIOD_START.plusDays(i))
                    .timeIn(LocalTime.of(8, 0))
                    .timeOut(LocalTime.of(17, 0))
                    .totalHours(new BigDecimal("8.00"))
                    .overtime(BigDecimal.ZERO)
                    .build());
        }
        return attendances;
    }

    @Nested
    class CreatePayrollTests {

        @Test
        void shouldCreatePayrollSuccessfully() {
            List<Attendance> attendances = createMockAttendances(10);

            when(payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(
                    employee.getId(), PERIOD_START, PERIOD_END)).thenReturn(false);
            when(employeeService.getEmployeeById(employee.getId())).thenReturn(employee);
            when(attendanceService.getEmployeeAttendances(employee.getId(), PERIOD_START, PERIOD_END))
                    .thenReturn(attendances);
            when(deductionTypeRepository.findByCode("SSS")).thenReturn(Optional.of(sssType));
            when(deductionTypeRepository.findByCode("PHIC")).thenReturn(Optional.of(phicType));
            when(deductionTypeRepository.findByCode("HDMF")).thenReturn(Optional.of(hdmfType));
            when(deductionTypeRepository.findByCode("TAX")).thenReturn(Optional.of(taxType));
            when(payrollRepository.save(any(Payroll.class))).thenReturn(payroll);

            Payroll result = payrollService.createPayroll(employee.getId(), PERIOD_START, PERIOD_END, PAY_DATE);

            assertNotNull(result);
            assertEquals(employee, result.getEmployee());
            assertEquals(PERIOD_START, result.getPeriodStartDate());
            assertEquals(PERIOD_END, result.getPeriodEndDate());
            assertEquals(PAY_DATE, result.getPayDate());

            ArgumentCaptor<Payroll> payrollCaptor = ArgumentCaptor.forClass(Payroll.class);
            verify(payrollRepository).save(payrollCaptor.capture());
            Payroll savedPayroll = payrollCaptor.getValue();

            assertEquals(10, savedPayroll.getDaysWorked());
            assertNotNull(savedPayroll.getGrossPay());
            assertNotNull(savedPayroll.getNetPay());
        }

        @Test
        void shouldThrowConflictWhenPayrollAlreadyExists() {
            when(payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(
                    employee.getId(), PERIOD_START, PERIOD_END)).thenReturn(true);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> payrollService.createPayroll(employee.getId(), PERIOD_START, PERIOD_END, PAY_DATE));

            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
            verify(payrollRepository, never()).save(any(Payroll.class));
        }

        @Test
        void shouldHandleEmployeeWithNoAttendances() {
            when(payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(
                    employee.getId(), PERIOD_START, PERIOD_END)).thenReturn(false);
            when(employeeService.getEmployeeById(employee.getId())).thenReturn(employee);
            when(attendanceService.getEmployeeAttendances(employee.getId(), PERIOD_START, PERIOD_END))
                    .thenReturn(Collections.emptyList());
            when(deductionTypeRepository.findByCode("SSS")).thenReturn(Optional.of(sssType));
            when(deductionTypeRepository.findByCode("PHIC")).thenReturn(Optional.of(phicType));
            when(deductionTypeRepository.findByCode("HDMF")).thenReturn(Optional.of(hdmfType));
            when(deductionTypeRepository.findByCode("TAX")).thenReturn(Optional.of(taxType));
            when(payrollRepository.save(any(Payroll.class))).thenReturn(payroll);

            Payroll result = payrollService.createPayroll(employee.getId(), PERIOD_START, PERIOD_END, PAY_DATE);

            assertNotNull(result);
            ArgumentCaptor<Payroll> payrollCaptor = ArgumentCaptor.forClass(Payroll.class);
            verify(payrollRepository).save(payrollCaptor.capture());
            Payroll savedPayroll = payrollCaptor.getValue();

            assertEquals(0, savedPayroll.getDaysWorked());
        }

        @Test
        void shouldHandleEmployeeWithOvertimeHours() {
            List<Attendance> attendances = new ArrayList<>();
            attendances.add(Attendance.builder()
                    .id(UUID.randomUUID())
                    .employee(employee)
                    .date(PERIOD_START)
                    .timeIn(LocalTime.of(8, 0))
                    .timeOut(LocalTime.of(20, 0))
                    .totalHours(new BigDecimal("12.00"))
                    .overtime(new BigDecimal("4.00"))
                    .build());

            when(payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(
                    employee.getId(), PERIOD_START, PERIOD_END)).thenReturn(false);
            when(employeeService.getEmployeeById(employee.getId())).thenReturn(employee);
            when(attendanceService.getEmployeeAttendances(employee.getId(), PERIOD_START, PERIOD_END))
                    .thenReturn(attendances);
            when(deductionTypeRepository.findByCode("SSS")).thenReturn(Optional.of(sssType));
            when(deductionTypeRepository.findByCode("PHIC")).thenReturn(Optional.of(phicType));
            when(deductionTypeRepository.findByCode("HDMF")).thenReturn(Optional.of(hdmfType));
            when(deductionTypeRepository.findByCode("TAX")).thenReturn(Optional.of(taxType));
            when(payrollRepository.save(any(Payroll.class))).thenReturn(payroll);

            Payroll result = payrollService.createPayroll(employee.getId(), PERIOD_START, PERIOD_END, PAY_DATE);

            assertNotNull(result);
            verify(payrollRepository).save(any(Payroll.class));
        }

        @Test
        void shouldThrowNotFoundWhenDeductionTypeNotFound() {
            List<Attendance> attendances = createMockAttendances(10);

            when(payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(
                    employee.getId(), PERIOD_START, PERIOD_END)).thenReturn(false);
            when(employeeService.getEmployeeById(employee.getId())).thenReturn(employee);
            when(attendanceService.getEmployeeAttendances(employee.getId(), PERIOD_START, PERIOD_END))
                    .thenReturn(attendances);
            when(deductionTypeRepository.findByCode("SSS")).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class,
                    () -> payrollService.createPayroll(employee.getId(), PERIOD_START, PERIOD_END, PAY_DATE));

            verify(payrollRepository, never()).save(any(Payroll.class));
        }

        @Test
        void shouldCalculateCorrectDeductions() {
            List<Attendance> attendances = createMockAttendances(10);

            when(payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(
                    employee.getId(), PERIOD_START, PERIOD_END)).thenReturn(false);
            when(employeeService.getEmployeeById(employee.getId())).thenReturn(employee);
            when(attendanceService.getEmployeeAttendances(employee.getId(), PERIOD_START, PERIOD_END))
                    .thenReturn(attendances);
            when(deductionTypeRepository.findByCode("SSS")).thenReturn(Optional.of(sssType));
            when(deductionTypeRepository.findByCode("PHIC")).thenReturn(Optional.of(phicType));
            when(deductionTypeRepository.findByCode("HDMF")).thenReturn(Optional.of(hdmfType));
            when(deductionTypeRepository.findByCode("TAX")).thenReturn(Optional.of(taxType));
            when(payrollRepository.save(any(Payroll.class))).thenReturn(payroll);

            payrollService.createPayroll(employee.getId(), PERIOD_START, PERIOD_END, PAY_DATE);

            ArgumentCaptor<Payroll> payrollCaptor = ArgumentCaptor.forClass(Payroll.class);
            verify(payrollRepository).save(payrollCaptor.capture());
            Payroll savedPayroll = payrollCaptor.getValue();

            assertEquals(4, savedPayroll.getDeductions().size());
            assertTrue(savedPayroll.getDeductions().stream()
                    .anyMatch(d -> d.getDeductionType().getCode().equals("SSS")));
            assertTrue(savedPayroll.getDeductions().stream()
                    .anyMatch(d -> d.getDeductionType().getCode().equals("PHIC")));
            assertTrue(savedPayroll.getDeductions().stream()
                    .anyMatch(d -> d.getDeductionType().getCode().equals("HDMF")));
            assertTrue(savedPayroll.getDeductions().stream()
                    .anyMatch(d -> d.getDeductionType().getCode().equals("TAX")));
        }

        @Test
        void shouldIncludeBenefitsInPayroll() {
            List<Attendance> attendances = createMockAttendances(10);

            when(payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(
                    employee.getId(), PERIOD_START, PERIOD_END)).thenReturn(false);
            when(employeeService.getEmployeeById(employee.getId())).thenReturn(employee);
            when(attendanceService.getEmployeeAttendances(employee.getId(), PERIOD_START, PERIOD_END))
                    .thenReturn(attendances);
            when(deductionTypeRepository.findByCode("SSS")).thenReturn(Optional.of(sssType));
            when(deductionTypeRepository.findByCode("PHIC")).thenReturn(Optional.of(phicType));
            when(deductionTypeRepository.findByCode("HDMF")).thenReturn(Optional.of(hdmfType));
            when(deductionTypeRepository.findByCode("TAX")).thenReturn(Optional.of(taxType));
            when(payrollRepository.save(any(Payroll.class))).thenReturn(payroll);

            payrollService.createPayroll(employee.getId(), PERIOD_START, PERIOD_END, PAY_DATE);

            ArgumentCaptor<Payroll> payrollCaptor = ArgumentCaptor.forClass(Payroll.class);
            verify(payrollRepository).save(payrollCaptor.capture());
            Payroll savedPayroll = payrollCaptor.getValue();

            assertNotNull(savedPayroll.getBenefits());
            assertEquals(1, savedPayroll.getBenefits().size());
            assertEquals(riceAllowanceType, savedPayroll.getBenefits().get(0).getBenefitType());
        }
    }

    @Nested
    class GetPayrollByIdTests {

        @Test
        void shouldReturnPayrollForPayrollUser() {
            when(userService.getAuthenticatedUser()).thenReturn(payrollUser);
            when(payrollRepository.findById(payroll.getId())).thenReturn(Optional.of(payroll));

            Payroll result = payrollService.getPayrollById(payroll.getId());

            assertNotNull(result);
            assertEquals(payroll.getId(), result.getId());
            verify(payrollRepository).findById(payroll.getId());
        }

        @Test
        void shouldAllowEmployeeToViewOwnPayroll() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            payroll.setEmployee(employee);
            when(payrollRepository.findById(payroll.getId())).thenReturn(Optional.of(payroll));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> payrollService.getPayrollById(payroll.getId()));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        void shouldThrowUnauthorizedWhenPrincipalIsNotUser() {
            when(userService.getAuthenticatedUser()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> payrollService.getPayrollById(payroll.getId()));

            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
            verify(payrollRepository, never()).findById(any());
        }

        @Test
        void shouldThrowNotFoundWhenPayrollDoesNotExist() {
            when(userService.getAuthenticatedUser()).thenReturn(payrollUser);
            UUID nonExistentId = UUID.randomUUID();
            when(payrollRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> payrollService.getPayrollById(nonExistentId));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        void shouldThrowForbiddenWhenPayrollUserAccessesOtherEmployeePayroll() {
            when(userService.getAuthenticatedUser()).thenReturn(payrollUser);
            payroll.setEmployee(otherEmployee);
            when(payrollRepository.findById(payroll.getId())).thenReturn(Optional.of(payroll));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> payrollService.getPayrollById(payroll.getId()));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        void shouldThrowForbiddenWhenNonPayrollUserTries() {
            when(userService.getAuthenticatedUser()).thenReturn(hrUser);
            payroll.setEmployee(employee);
            when(payrollRepository.findById(payroll.getId())).thenReturn(Optional.of(payroll));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> payrollService.getPayrollById(payroll.getId()));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }
    }

    @Nested
    class GetAllPayrollTests {

        @Test
        void shouldReturnAllPayrollWithinDateRange() {
            List<Payroll> payrolls = List.of(payroll);
            Page<Payroll> page = new PageImpl<>(payrolls);
            Pageable pageable = PageRequest.of(0, 10);
            DateRange dateRange = new DateRange(PERIOD_START, PERIOD_END);

            when(dateRangeResolver.resolve(PERIOD_START, PERIOD_END)).thenReturn(dateRange);
            when(payrollRepository.findAllByPeriodStartDateBetween(PERIOD_START, PERIOD_END, pageable))
                    .thenReturn(page);

            Page<Payroll> result = payrollService.getAllPayroll(0, 10, PERIOD_START, PERIOD_END);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            verify(payrollRepository).findAllByPeriodStartDateBetween(PERIOD_START, PERIOD_END, pageable);
        }

        @Test
        void shouldReturnEmptyPageWhenNoPayrollsFound() {
            Page<Payroll> emptyPage = new PageImpl<>(Collections.emptyList());
            Pageable pageable = PageRequest.of(0, 10);
            DateRange dateRange = new DateRange(PERIOD_START, PERIOD_END);

            when(dateRangeResolver.resolve(PERIOD_START, PERIOD_END)).thenReturn(dateRange);
            when(payrollRepository.findAllByPeriodStartDateBetween(PERIOD_START, PERIOD_END, pageable))
                    .thenReturn(emptyPage);

            Page<Payroll> result = payrollService.getAllPayroll(0, 10, PERIOD_START, PERIOD_END);

            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
        }

        @Test
        void shouldHandlePaginationCorrectly() {
            List<Payroll> payrolls = Arrays.asList(payroll, payroll, payroll);
            Page<Payroll> page = new PageImpl<>(payrolls, PageRequest.of(1, 2), 5);
            DateRange dateRange = new DateRange(PERIOD_START, PERIOD_END);

            when(dateRangeResolver.resolve(PERIOD_START, PERIOD_END)).thenReturn(dateRange);
            when(payrollRepository.findAllByPeriodStartDateBetween(
                    eq(PERIOD_START), eq(PERIOD_END), any(Pageable.class))).thenReturn(page);

            Page<Payroll> result = payrollService.getAllPayroll(1, 2, PERIOD_START, PERIOD_END);

            assertNotNull(result);
            assertEquals(3, result.getContent().size());
            assertEquals(5, result.getTotalElements());
            assertEquals(1, result.getNumber());
        }

        @Test
        void shouldUseDateRangeResolver() {
            DateRange dateRange = new DateRange(PERIOD_START.minusDays(5), PERIOD_END.plusDays(5));
            Page<Payroll> page = new PageImpl<>(Collections.emptyList());

            when(dateRangeResolver.resolve(PERIOD_START, PERIOD_END)).thenReturn(dateRange);
            when(payrollRepository.findAllByPeriodStartDateBetween(
                    eq(dateRange.startDate()), eq(dateRange.endDate()), any(Pageable.class))).thenReturn(page);

            payrollService.getAllPayroll(0, 10, PERIOD_START, PERIOD_END);

            verify(dateRangeResolver).resolve(PERIOD_START, PERIOD_END);
            verify(payrollRepository).findAllByPeriodStartDateBetween(
                    eq(dateRange.startDate()), eq(dateRange.endDate()), any(Pageable.class));
        }
    }

    @Nested
    class GetAllEmployeePayrollTests {

        @Test
        void shouldReturnEmployeePayrolls() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            List<Payroll> payrolls = List.of(payroll);
            Page<Payroll> page = new PageImpl<>(payrolls);
            DateRange dateRange = new DateRange(PERIOD_START, PERIOD_END);

            when(dateRangeResolver.resolve(PERIOD_START, PERIOD_END)).thenReturn(dateRange);
            when(payrollRepository.findAllByEmployee_IdAndPeriodStartDateBetween(
                    eq(employee.getId()), eq(PERIOD_START), eq(PERIOD_END), any(Pageable.class)))
                    .thenReturn(page);

            Page<Payroll> result = payrollService.getAllEmployeePayroll(0, 10, PERIOD_START, PERIOD_END);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            verify(payrollRepository).findAllByEmployee_IdAndPeriodStartDateBetween(
                    eq(employee.getId()), eq(PERIOD_START), eq(PERIOD_END), any(Pageable.class));
        }

        @Test
        void shouldThrowUnauthorizedWhenPrincipalIsNotUser() {
            when(userService.getAuthenticatedUser()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> payrollService.getAllEmployeePayroll(0, 10, PERIOD_START, PERIOD_END));
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());

            verify(payrollRepository, never()).findAllByEmployee_IdAndPeriodStartDateBetween(
                    anyLong(), any(), any(), any());
        }

        @Test
        void shouldReturnEmptyPageWhenEmployeeHasNoPayrolls() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            Page<Payroll> emptyPage = new PageImpl<>(Collections.emptyList());
            DateRange dateRange = new DateRange(PERIOD_START, PERIOD_END);

            when(dateRangeResolver.resolve(PERIOD_START, PERIOD_END)).thenReturn(dateRange);
            when(payrollRepository.findAllByEmployee_IdAndPeriodStartDateBetween(
                    eq(employee.getId()), eq(PERIOD_START), eq(PERIOD_END), any(Pageable.class)))
                    .thenReturn(emptyPage);

            Page<Payroll> result = payrollService.getAllEmployeePayroll(0, 10, PERIOD_START, PERIOD_END);

            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
        }

        @Test
        void shouldOnlyReturnPayrollsForAuthenticatedEmployee() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            DateRange dateRange = new DateRange(PERIOD_START, PERIOD_END);
            Page<Payroll> page = new PageImpl<>(List.of(payroll));

            when(dateRangeResolver.resolve(PERIOD_START, PERIOD_END)).thenReturn(dateRange);
            when(payrollRepository.findAllByEmployee_IdAndPeriodStartDateBetween(
                    eq(employee.getId()), eq(PERIOD_START), eq(PERIOD_END), any(Pageable.class)))
                    .thenReturn(page);

            payrollService.getAllEmployeePayroll(0, 10, PERIOD_START, PERIOD_END);

            verify(payrollRepository).findAllByEmployee_IdAndPeriodStartDateBetween(
                    eq(employee.getId()), eq(PERIOD_START), eq(PERIOD_END), any(Pageable.class));
            verify(payrollRepository, never()).findAllByEmployee_IdAndPeriodStartDateBetween(
                    eq(otherEmployee.getId()), any(), any(), any());
        }

        @Test
        void shouldHandlePaginationForEmployeePayrolls() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            List<Payroll> payrolls = Arrays.asList(payroll, payroll);
            Page<Payroll> page = new PageImpl<>(payrolls, PageRequest.of(0, 2), 10);
            DateRange dateRange = new DateRange(PERIOD_START, PERIOD_END);

            when(dateRangeResolver.resolve(PERIOD_START, PERIOD_END)).thenReturn(dateRange);
            when(payrollRepository.findAllByEmployee_IdAndPeriodStartDateBetween(
                    eq(employee.getId()), eq(PERIOD_START), eq(PERIOD_END), any(Pageable.class)))
                    .thenReturn(page);

            Page<Payroll> result = payrollService.getAllEmployeePayroll(0, 2, PERIOD_START, PERIOD_END);

            assertNotNull(result);
            assertEquals(2, result.getContent().size());
            assertEquals(10, result.getTotalElements());
        }

        @Test
        void shouldUseDateRangeResolverForEmployeePayrolls() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            DateRange customRange = new DateRange(PERIOD_START.minusDays(10), PERIOD_END.plusDays(10));
            Page<Payroll> page = new PageImpl<>(Collections.emptyList());

            when(dateRangeResolver.resolve(PERIOD_START, PERIOD_END)).thenReturn(customRange);
            when(payrollRepository.findAllByEmployee_IdAndPeriodStartDateBetween(
                    eq(employee.getId()), eq(customRange.startDate()), eq(customRange.endDate()), any(Pageable.class)))
                    .thenReturn(page);

            payrollService.getAllEmployeePayroll(0, 10, PERIOD_START, PERIOD_END);

            verify(dateRangeResolver).resolve(PERIOD_START, PERIOD_END);
            verify(payrollRepository).findAllByEmployee_IdAndPeriodStartDateBetween(
                    eq(employee.getId()), eq(customRange.startDate()), eq(customRange.endDate()), any(Pageable.class));
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void shouldHandleNullBenefitsList() {
            employee.setBenefits(Collections.emptyList());

            List<Attendance> attendances = createMockAttendances(10);

            when(payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(
                    employee.getId(), PERIOD_START, PERIOD_END)).thenReturn(false);
            when(employeeService.getEmployeeById(employee.getId())).thenReturn(employee);
            when(attendanceService.getEmployeeAttendances(employee.getId(), PERIOD_START, PERIOD_END))
                    .thenReturn(attendances);
            when(deductionTypeRepository.findByCode("SSS")).thenReturn(Optional.of(sssType));
            when(deductionTypeRepository.findByCode("PHIC")).thenReturn(Optional.of(phicType));
            when(deductionTypeRepository.findByCode("HDMF")).thenReturn(Optional.of(hdmfType));
            when(deductionTypeRepository.findByCode("TAX")).thenReturn(Optional.of(taxType));
            when(payrollRepository.save(any(Payroll.class))).thenReturn(payroll);

            Payroll result = payrollService.createPayroll(employee.getId(), PERIOD_START, PERIOD_END, PAY_DATE);

            assertNotNull(result);
            ArgumentCaptor<Payroll> payrollCaptor = ArgumentCaptor.forClass(Payroll.class);
            verify(payrollRepository).save(payrollCaptor.capture());
            Payroll savedPayroll = payrollCaptor.getValue();

            assertTrue(savedPayroll.getBenefits().isEmpty());
        }

        @Test
        void shouldHandleVeryLowSalaryEmployee() {
            employee.setBasicSalary(new BigDecimal("5000.00"));
            employee.setHourlyRate(new BigDecimal("29.76"));

            List<Attendance> attendances = createMockAttendances(5);

            when(payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(
                    employee.getId(), PERIOD_START, PERIOD_END)).thenReturn(false);
            when(employeeService.getEmployeeById(employee.getId())).thenReturn(employee);
            when(attendanceService.getEmployeeAttendances(employee.getId(), PERIOD_START, PERIOD_END))
                    .thenReturn(attendances);
            when(deductionTypeRepository.findByCode("SSS")).thenReturn(Optional.of(sssType));
            when(deductionTypeRepository.findByCode("PHIC")).thenReturn(Optional.of(phicType));
            when(deductionTypeRepository.findByCode("HDMF")).thenReturn(Optional.of(hdmfType));
            when(deductionTypeRepository.findByCode("TAX")).thenReturn(Optional.of(taxType));
            when(payrollRepository.save(any(Payroll.class))).thenReturn(payroll);

            Payroll result = payrollService.createPayroll(employee.getId(), PERIOD_START, PERIOD_END, PAY_DATE);

            assertNotNull(result);
            verify(payrollRepository).save(any(Payroll.class));
        }

        @Test
        void shouldHandleMaxOvertimeHours() {
            List<Attendance> attendances = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                attendances.add(Attendance.builder()
                        .id(UUID.randomUUID())
                        .employee(employee)
                        .date(PERIOD_START.plusDays(i))
                        .timeIn(LocalTime.of(8, 0))
                        .timeOut(LocalTime.of(22, 0))
                        .totalHours(new BigDecimal("14.00"))
                        .overtime(new BigDecimal("6.00"))
                        .build());
            }

            when(payrollRepository.existsByEmployee_IdAndPeriodStartDateAndPeriodEndDate(
                    employee.getId(), PERIOD_START, PERIOD_END)).thenReturn(false);
            when(employeeService.getEmployeeById(employee.getId())).thenReturn(employee);
            when(attendanceService.getEmployeeAttendances(employee.getId(), PERIOD_START, PERIOD_END))
                    .thenReturn(attendances);
            when(deductionTypeRepository.findByCode("SSS")).thenReturn(Optional.of(sssType));
            when(deductionTypeRepository.findByCode("PHIC")).thenReturn(Optional.of(phicType));
            when(deductionTypeRepository.findByCode("HDMF")).thenReturn(Optional.of(hdmfType));
            when(deductionTypeRepository.findByCode("TAX")).thenReturn(Optional.of(taxType));
            when(payrollRepository.save(any(Payroll.class))).thenReturn(payroll);

            Payroll result = payrollService.createPayroll(employee.getId(), PERIOD_START, PERIOD_END, PAY_DATE);

            assertNotNull(result);
            verify(payrollRepository).save(any(Payroll.class));
        }
    }
}

