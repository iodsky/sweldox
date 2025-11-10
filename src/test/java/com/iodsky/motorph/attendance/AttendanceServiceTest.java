package com.iodsky.motorph.attendance;

import com.iodsky.motorph.common.DateRange;
import com.iodsky.motorph.common.DateRangeResolver;
import com.iodsky.motorph.common.exception.*;
import com.iodsky.motorph.employee.EmployeeService;
import com.iodsky.motorph.employee.model.Employee;
import com.iodsky.motorph.security.user.User;
import com.iodsky.motorph.security.user.UserRole;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private AttendanceRepository attendanceRepository;
    @Mock private EmployeeService employeeService;
    @Mock private DateRangeResolver dateRangeResolver;
    @InjectMocks private AttendanceService attendanceService;

    private User hrUser;
    private User normalUser;
    private Employee currentEmployee;
    private Employee otherEmployee;
    private AttendanceDto dto;
    private Attendance attendance;

    private static final LocalDate TODAY = LocalDate.of(2025, 11, 1);
    private static final LocalTime SHIFT_START = LocalTime.of(8, 0);
    private static final LocalTime EARLIEST_SHIFT = SHIFT_START.minusMinutes(15);

    @BeforeEach
    void setUp() {
        currentEmployee = new Employee();
        currentEmployee.setId(1L);
        otherEmployee = new Employee();
        otherEmployee.setId(2L);

        hrUser = new User();
        hrUser.setUserRole(new UserRole("HR"));
        hrUser.setEmployee(currentEmployee);

        normalUser = new User();
        normalUser.setUserRole(new UserRole("EMPLOYEE"));
        normalUser.setEmployee(currentEmployee);

        dto = AttendanceDto.builder()
                .id(UUID.randomUUID())
                .employeeId(1L)
                .date(TODAY)
                .timeIn(SHIFT_START)
                .build();

        attendance = Attendance.builder()
                .id(UUID.randomUUID())
                .employee(currentEmployee)
                .date(TODAY)
                .timeIn(SHIFT_START)
                .timeOut(LocalTime.MIN)
                .totalHours(BigDecimal.ZERO)
                .overtime(BigDecimal.ZERO)
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

    @Nested
    class CreateAttendanceTests {
        @Test
        void shouldCreateAttendanceSuccessfullyForSelf() {
            mockAuth(normalUser);
            when(attendanceRepository.findByEmployee_IdAndDate(anyLong(), any())).thenReturn(Optional.empty());
            when(employeeService.getEmployeeById(anyLong())).thenReturn(currentEmployee);
            when(attendanceRepository.save(any())).thenReturn(attendance);

            Attendance result = attendanceService.createAttendance(dto);

            assertNotNull(result);
            verify(attendanceRepository).save(any(Attendance.class));
        }

        @Test
        void shouldAllowHrToCreateAttendanceForOthers() {
            mockAuth(hrUser);
            dto.setEmployeeId(otherEmployee.getId());
            when(attendanceRepository.findByEmployee_IdAndDate(anyLong(), any())).thenReturn(Optional.empty());
            when(employeeService.getEmployeeById(anyLong())).thenReturn(otherEmployee);
            when(attendanceRepository.save(any())).thenReturn(attendance);

            Attendance result = attendanceService.createAttendance(dto);

            assertNotNull(result);
            verify(employeeService).getEmployeeById(otherEmployee.getId());
        }

        @Test
        void shouldThrowUnauthorizedWhenPrincipalIsNotUser() {
            Authentication auth = mock(Authentication.class);
            SecurityContext context = mock(SecurityContext.class);
            when(auth.getPrincipal()).thenReturn("anonymousUser");
            when(context.getAuthentication()).thenReturn(auth);
            SecurityContextHolder.setContext(context);

            assertThrows(UnauthorizedException.class, () -> attendanceService.createAttendance(dto));
        }

        @Test
        void shouldThrowForbiddenWhenNonHrCreatesForOthers() {
            mockAuth(normalUser);
            dto.setEmployeeId(otherEmployee.getId());

            assertThrows(ForbiddenException.class, () -> attendanceService.createAttendance(dto));
        }

        @Test
        void shouldThrowBadRequestWhenClockInTooEarly() {
            mockAuth(normalUser);
            dto.setTimeIn(EARLIEST_SHIFT.minusMinutes(1)); // too early

            assertThrows(BadRequestException.class, () -> attendanceService.createAttendance(dto));
        }

        @Test
        void shouldThrowConflictWhenAttendanceAlreadyExists() {
            mockAuth(normalUser);
            when(attendanceRepository.findByEmployee_IdAndDate(anyLong(), any())).thenReturn(Optional.of(attendance));

            assertThrows(ConflictException.class, () -> attendanceService.createAttendance(dto));
        }
    }

    @Nested
    class UpdateAttendanceTests {
        @Test
        @DisabledIfEnvironmentVariable(named = "CI", matches = "true")
        void shouldAllowEmployeeToClockOutSuccessfully() {
            mockAuth(normalUser);
            Attendance existing = Attendance.builder()
                    .id(UUID.randomUUID())
                    .employee(currentEmployee)
                    .date(TODAY)
                    .timeIn(SHIFT_START)
                    .timeOut(LocalTime.MIN)
                    .build();

            when(attendanceRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));
            when(attendanceRepository.save(any())).thenReturn(existing);

            Attendance result = attendanceService.updateAttendance(existing.getId(), null);

            assertNotNull(result.getTimeOut());
            verify(attendanceRepository).save(existing);
        }

        @Test
        void shouldThrowConflictWhenAlreadyClockedOut() {
            mockAuth(normalUser);
            Attendance existing = Attendance.builder()
                    .id(UUID.randomUUID())
                    .employee(currentEmployee)
                    .date(TODAY)
                    .timeIn(SHIFT_START)
                    .timeOut(LocalTime.of(17, 0))
                    .build();

            when(attendanceRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));

            assertThrows(ConflictException.class, () -> attendanceService.updateAttendance(existing.getId(), null));
        }

        @Test
        void shouldThrowBadRequestWhenClockOutBeforeTimeIn() {
            mockAuth(normalUser);
            Attendance existing = Attendance.builder()
                    .id(UUID.randomUUID())
                    .employee(currentEmployee)
                    .date(TODAY)
                    .timeIn(LocalTime.now())
                    .timeOut(LocalTime.MIN)
                    .build();

            LocalTime earlierTime = existing.getTimeIn().minusHours(1);

            when(attendanceRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));

            try (MockedStatic<LocalTime> mocked = mockStatic(LocalTime.class)) {
                mocked.when(LocalTime::now).thenReturn(earlierTime);
                assertThrows(BadRequestException.class,
                        () -> attendanceService.updateAttendance(existing.getId(), null));
            }
        }

        @Test
        void shouldAllowHrToUpdateAttendanceManually() {
            mockAuth(hrUser);
            Attendance existing = Attendance.builder()
                    .id(UUID.randomUUID())
                    .employee(currentEmployee)
                    .date(TODAY)
                    .timeIn(SHIFT_START)
                    .timeOut(LocalTime.MIN)
                    .build();

            dto.setTimeOut(LocalTime.of(18, 0));

            when(attendanceRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));
            when(attendanceRepository.save(any())).thenReturn(existing);

            Attendance result = attendanceService.updateAttendance(existing.getId(), dto);

            assertEquals(dto.getTimeOut(), result.getTimeOut());
            verify(attendanceRepository).save(existing);
        }

        @Test
        void shouldThrowForbiddenWhenNonHrTriesToEditAttendanceManually() {
            mockAuth(normalUser);
            Attendance existing = Attendance.builder()
                    .id(UUID.randomUUID())
                    .employee(currentEmployee)
                    .date(TODAY)
                    .timeIn(SHIFT_START)
                    .timeOut(LocalTime.MIN)
                    .build();

            when(attendanceRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));

            assertThrows(ForbiddenException.class, () -> attendanceService.updateAttendance(existing.getId(), dto));
        }

        @Test
        void shouldThrowNotFoundWhenAttendanceDoesNotExist() {
            mockAuth(normalUser);
            when(attendanceRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> attendanceService.updateAttendance(UUID.randomUUID(), dto));
        }
    }

    @Nested
    class GetAllAttendancesTests {
        @Test
        void shouldReturnAllAttendancesForSpecificDate() {
            when(attendanceRepository.findAllByDate(TODAY)).thenReturn(List.of(attendance));

            List<Attendance> result = attendanceService.getAllAttendances(TODAY, null);

            assertEquals(1, result.size());
        }

        @Test
        void shouldReturnAttendancesForDateRange() {
            when(dateRangeResolver.resolve(any(), any()))
                    .thenReturn(new DateRange(TODAY, TODAY.plusDays(2)));
            when(attendanceRepository.findAllByDateBetween(any(), any())).thenReturn(List.of(attendance));

            List<Attendance> result = attendanceService.getAllAttendances(TODAY, TODAY.plusDays(2));

            assertFalse(result.isEmpty());
        }

        @Test
        void shouldHandleSwappedDates() {
            when(dateRangeResolver.resolve(any(), any()))
                    .thenReturn(new DateRange(TODAY, TODAY.plusDays(2)));

            when(attendanceRepository.findAllByDateBetween(any(), any())).thenReturn(List.of(attendance));

            List<Attendance> result = attendanceService.getAllAttendances(TODAY.plusDays(2), TODAY);

            assertFalse(result.isEmpty());
            verify(attendanceRepository).findAllByDateBetween(any(LocalDate.class), any(LocalDate.class));
        }
    }

    @Nested
    class GetEmployeeAttendancesTests {
        @Test
        void shouldAllowEmployeeToGetOwnAttendances() {
            mockAuth(normalUser);
            when(attendanceRepository.findByEmployee_IdAndDateBetween(anyLong(), any(), any()))
                    .thenReturn(List.of(attendance));
            when(dateRangeResolver.resolve(any(), any()))
                    .thenReturn(new DateRange(TODAY, TODAY.plusDays(2)));

            List<Attendance> result = attendanceService.getEmployeeAttendances(null, TODAY, TODAY.plusDays(1));

            assertEquals(1, result.size());
        }

        @Test
        void shouldAllowHrToGetAnyEmployeeAttendances() {
            mockAuth(hrUser);
            when(attendanceRepository.findByEmployee_IdAndDateBetween(anyLong(), any(), any()))
                    .thenReturn(List.of(attendance));
            when(dateRangeResolver.resolve(any(), any()))
                    .thenReturn(new DateRange(TODAY, TODAY.plusDays(2)));

            List<Attendance> result = attendanceService.getEmployeeAttendances(otherEmployee.getId(), TODAY, TODAY.plusDays(1));

            assertEquals(1, result.size());
        }

        @Test
        void shouldThrowForbiddenWhenNonHrRequestsOthersData() {
            mockAuth(normalUser);

            assertThrows(ForbiddenException.class,
                    () -> attendanceService.getEmployeeAttendances(otherEmployee.getId(), TODAY, TODAY.plusDays(1)));
        }

        @Test
        void shouldThrowUnauthorizedWhenNoAuth() {
            Authentication auth = mock(Authentication.class);
            SecurityContext context = mock(SecurityContext.class);
            when(auth.getPrincipal()).thenReturn("anonymousUser");
            when(context.getAuthentication()).thenReturn(auth);
            SecurityContextHolder.setContext(context);

            assertThrows(UnauthorizedException.class,
                    () -> attendanceService.getEmployeeAttendances(1L, TODAY, TODAY.plusDays(1)));
        }
    }

}
