package com.iodsky.sweldox.attendance;

import com.iodsky.sweldox.common.DateRange;
import com.iodsky.sweldox.common.DateRangeResolver;
import com.iodsky.sweldox.employee.EmployeeService;
import com.iodsky.sweldox.employee.Employee;
import com.iodsky.sweldox.security.user.User;
import com.iodsky.sweldox.security.user.UserRole;
import com.iodsky.sweldox.security.user.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock private AttendanceRepository attendanceRepository;
    @Mock private EmployeeService employeeService;
    @Mock private UserService userService;
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

    @Nested
    class CreateAttendanceTests {
        @Test
        void shouldCreateAttendanceSuccessfullyForSelf() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            when(attendanceRepository.findByEmployee_IdAndDate(anyLong(), any())).thenReturn(Optional.empty());
            when(employeeService.getEmployeeById(anyLong())).thenReturn(currentEmployee);
            when(attendanceRepository.save(any())).thenReturn(attendance);

            Attendance result = attendanceService.createAttendance(dto);

            assertNotNull(result);
            verify(attendanceRepository).save(any(Attendance.class));
        }

        @Test
        void shouldAllowHrToCreateAttendanceForOthers() {
            when(userService.getAuthenticatedUser()).thenReturn(hrUser);
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
            when(userService.getAuthenticatedUser()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> attendanceService.createAttendance(dto));

            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }

        @Test
        void shouldThrowForbiddenWhenNonHrCreatesForOthers() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            dto.setEmployeeId(otherEmployee.getId());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> attendanceService.createAttendance(dto));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        void shouldThrowBadRequestWhenClockInTooEarly() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            dto.setTimeIn(EARLIEST_SHIFT.minusMinutes(1)); // too early

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> attendanceService.createAttendance(dto));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        void shouldThrowConflictWhenAttendanceAlreadyExists() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            when(attendanceRepository.findByEmployee_IdAndDate(anyLong(), any())).thenReturn(Optional.of(attendance));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> attendanceService.createAttendance(dto));

            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }
    }

    @Nested
    class UpdateAttendanceTests {
        @Test
        void shouldAllowEmployeeToClockOutSuccessfully() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            Attendance existing = Attendance.builder()
                    .id(UUID.randomUUID())
                    .employee(currentEmployee)
                    .date(TODAY)
                    .timeIn(SHIFT_START)
                    .timeOut(LocalTime.MIN)
                    .build();

            LocalTime clockOutTime = SHIFT_START.plusHours(8);

            when(attendanceRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));
            when(attendanceRepository.save(any())).thenReturn(existing);

            try (MockedStatic<LocalTime> mocked = mockStatic(LocalTime.class, CALLS_REAL_METHODS)) {
                mocked.when(LocalTime::now).thenReturn(clockOutTime);
                Attendance result = attendanceService.updateAttendance(existing.getId(), null);

                assertNotNull(result.getTimeOut());
                assertEquals(clockOutTime, result.getTimeOut());
                verify(attendanceRepository).save(existing);
            }
        }

        @Test
        void shouldThrowConflictWhenAlreadyClockedOut() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            Attendance existing = Attendance.builder()
                    .id(UUID.randomUUID())
                    .employee(currentEmployee)
                    .date(TODAY)
                    .timeIn(SHIFT_START)
                    .timeOut(LocalTime.of(17, 0))
                    .build();

            when(attendanceRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> attendanceService.updateAttendance(existing.getId(), null));

            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }

        @Test
        void shouldThrowBadRequestWhenClockOutBeforeTimeIn() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            Attendance existing = Attendance.builder()
                    .id(UUID.randomUUID())
                    .employee(currentEmployee)
                    .date(TODAY)
                    .timeIn(LocalTime.now())
                    .timeOut(LocalTime.MIN)
                    .build();

            LocalTime earlierTime = existing.getTimeIn().minusHours(1);

            when(attendanceRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));

            try (MockedStatic<LocalTime> mocked = mockStatic(LocalTime.class, CALLS_REAL_METHODS)) {
                mocked.when(LocalTime::now).thenReturn(earlierTime);

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                        () -> attendanceService.updateAttendance(existing.getId(), null));

                assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            }
        }

        @Test
        void shouldAllowHrToUpdateAttendanceManually() {
            when(userService.getAuthenticatedUser()).thenReturn(hrUser);
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
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            Attendance existing = Attendance.builder()
                    .id(UUID.randomUUID())
                    .employee(currentEmployee)
                    .date(TODAY)
                    .timeIn(SHIFT_START)
                    .timeOut(LocalTime.MIN)
                    .build();

            when(attendanceRepository.findById(any(UUID.class))).thenReturn(Optional.of(existing));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> attendanceService.updateAttendance(existing.getId(), dto));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        void shouldThrowNotFoundWhenAttendanceDoesNotExist() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            when(attendanceRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> attendanceService.updateAttendance(UUID.randomUUID(), dto));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    @Nested
    class GetAllAttendancesTests {
        @Test
        void shouldReturnAllAttendancesForSpecificDate() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Attendance> attendancePage = new PageImpl<>(List.of(attendance), pageable, 1);
            when(attendanceRepository.findAllByDate(eq(TODAY), any(Pageable.class))).thenReturn(attendancePage);

            Page<Attendance> result = attendanceService.getAllAttendances(0, 10, TODAY, null);

            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getContent().size());
            verify(attendanceRepository).findAllByDate(eq(TODAY), any(Pageable.class));
        }

        @Test
        void shouldReturnAttendancesForDateRange() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Attendance> attendancePage = new PageImpl<>(List.of(attendance), pageable, 1);
            when(dateRangeResolver.resolve(any(), any()))
                    .thenReturn(new DateRange(TODAY, TODAY.plusDays(2)));
            when(attendanceRepository.findAllByDateBetween(any(), any(), any(Pageable.class))).thenReturn(attendancePage);

            Page<Attendance> result = attendanceService.getAllAttendances(0, 10, TODAY, TODAY.plusDays(2));

            assertFalse(result.isEmpty());
            assertEquals(1, result.getContent().size());
        }

        @Test
        void shouldHandleSwappedDates() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Attendance> attendancePage = new PageImpl<>(List.of(attendance), pageable, 1);
            when(dateRangeResolver.resolve(any(), any()))
                    .thenReturn(new DateRange(TODAY, TODAY.plusDays(2)));

            when(attendanceRepository.findAllByDateBetween(any(), any(), any(Pageable.class))).thenReturn(attendancePage);

            Page<Attendance> result = attendanceService.getAllAttendances(0, 10, TODAY.plusDays(2), TODAY);

            assertFalse(result.isEmpty());
            verify(attendanceRepository).findAllByDateBetween(any(LocalDate.class), any(LocalDate.class), any(Pageable.class));
        }
    }

    @Nested
    class GetEmployeeAttendancesTests {
        @Test
        void shouldAllowEmployeeToGetOwnAttendances() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Attendance> attendancePage = new PageImpl<>(List.of(attendance), pageable, 1);
            when(attendanceRepository.findByEmployee_IdAndDateBetween(anyLong(), any(), any(), any(Pageable.class)))
                    .thenReturn(attendancePage);
            when(dateRangeResolver.resolve(any(), any()))
                    .thenReturn(new DateRange(TODAY, TODAY.plusDays(2)));

            Page<Attendance> result = attendanceService.getEmployeeAttendances(0, 10, null, TODAY, TODAY.plusDays(1));

            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getContent().size());
        }

        @Test
        void shouldAllowHrToGetAnyEmployeeAttendances() {
            when(userService.getAuthenticatedUser()).thenReturn(hrUser);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Attendance> attendancePage = new PageImpl<>(List.of(attendance), pageable, 1);
            when(attendanceRepository.findByEmployee_IdAndDateBetween(anyLong(), any(), any(), any(Pageable.class)))
                    .thenReturn(attendancePage);
            when(dateRangeResolver.resolve(any(), any()))
                    .thenReturn(new DateRange(TODAY, TODAY.plusDays(2)));

            Page<Attendance> result = attendanceService.getEmployeeAttendances(0, 10, otherEmployee.getId(), TODAY, TODAY.plusDays(1));

            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getContent().size());
        }

        @Test
        void shouldThrowForbiddenWhenNonHrRequestsOthersData() {
            when(userService.getAuthenticatedUser()).thenReturn(normalUser);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> attendanceService.getEmployeeAttendances(0, 10, otherEmployee.getId(), TODAY, TODAY.plusDays(1)));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

        @Test
        void shouldThrowUnauthorizedWhenNoAuth() {
            when(userService.getAuthenticatedUser()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> attendanceService.getEmployeeAttendances(0, 10, 1L, TODAY, TODAY.plusDays(1)));

            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }

        @Test
        void shouldSupportNonPaginatedVersionForBackwardCompatibility() {
            when(attendanceRepository.findByEmployee_IdAndDateBetween(anyLong(), any(), any()))
                    .thenReturn(List.of(attendance));

            List<Attendance> result = attendanceService.getEmployeeAttendances(1L, TODAY, TODAY.plusDays(1));

            assertEquals(1, result.size());
            verify(attendanceRepository).findByEmployee_IdAndDateBetween(eq(1L), eq(TODAY), eq(TODAY.plusDays(1)));
        }
    }

}
