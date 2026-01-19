package com.iodsky.sweldox.attendance;

import com.iodsky.sweldox.common.DateRange;
import com.iodsky.sweldox.common.DateRangeResolver;
import com.iodsky.sweldox.employee.EmployeeService;
import com.iodsky.sweldox.employee.Employee;
import com.iodsky.sweldox.security.user.User;
import com.iodsky.sweldox.security.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeService employeeService;
    private final UserService userService;
    private final DateRangeResolver dateRangeResolver;

    public Attendance createAttendance(AttendanceDto attendanceDto) {
        User user = userService.getAuthenticatedUser();

        boolean isHr = "HR".equalsIgnoreCase(user.getUserRole().getRole());

        // All roles may clock themselves in, but only HR can add for others
        Long currentEmployeeId = user.getEmployee().getId();

        // Determine target employee
        Long employeeId;
        if (attendanceDto == null || attendanceDto.getEmployeeId() == null) {
            // Self clock-in
            employeeId = currentEmployeeId;
        } else {
            employeeId = attendanceDto.getEmployeeId();

            // Authorization rule
            if (!isHr && !employeeId.equals(currentEmployeeId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have the permissions to access this resource");
            }
        }

        // Determine attendance date and time
        LocalDate attendanceDate = (attendanceDto != null && attendanceDto.getDate() != null)
                ? attendanceDto.getDate()
                : LocalDate.now();

        LocalTime clockInTime = (attendanceDto != null && attendanceDto.getTimeIn() != null)
                ? attendanceDto.getTimeIn()
                : LocalTime.now();


        // Check for existing attendance record
        Optional<Attendance> exists = attendanceRepository.findByEmployee_IdAndDate(employeeId, attendanceDate);
        if (exists.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attendance record already exists");
        }

        // Build attendance
        Employee employee = employeeService.getEmployeeById(employeeId);

        Attendance attendance = Attendance.builder()
                .employee(employee)
                .date(attendanceDate)
                .timeIn(clockInTime)
                .timeOut(LocalTime.MIN)
                .totalHours(BigDecimal.ZERO)
                .overtime(BigDecimal.ZERO)
                .build();

        return attendanceRepository.save(attendance);
    }

    public Attendance updateAttendance(UUID id, AttendanceDto attendanceDto) {
        User user = userService.getAuthenticatedUser();

        boolean isHr = "HR".equalsIgnoreCase(user.getUserRole().getRole());

        Attendance attendance = attendanceRepository.findById(id)
                // Not yet clocked in
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance not found with id: " + id));

        long currentEmpId = user.getEmployee().getId();
        long employeeId = attendance.getEmployee().getId();

        if (!isHr && employeeId != currentEmpId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have the permissions to access this resource");
        }

        if (attendanceDto == null) {
            if (attendance.getTimeOut() != null && !attendance.getTimeOut().equals(LocalTime.MIN)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already clocked out for the day.");
            }

            LocalTime clockOut = LocalTime.now();
            if (clockOut.isBefore(attendance.getTimeIn())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clock-out time cannot be before clock-in time");
            }

            attendance.setTimeOut(clockOut);
        }
        else {
            if (!isHr) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have the permissions to access this resource");
            }

            if (attendanceDto.getTimeIn() != null) {
                attendance.setTimeIn(attendanceDto.getTimeIn());
            }

            if (attendanceDto.getTimeOut() != null) {
                attendance.setTimeOut(attendanceDto.getTimeOut());
            }

            if (attendanceDto.getDate() != null) {
                attendance.setDate(attendanceDto.getDate());
            }
        }

        if (attendance.getTimeIn() != null && attendance.getTimeOut() != null && !attendance.getTimeOut().equals(LocalTime.MIN)) {
            Employee employee = attendance.getEmployee();

            if ( employee.getStartShift() == null
                    || employee.getEndShift() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Employee shift times are not configured. Cannot calculate hours.");
            }

            LocalTime employeeStartShift = employee.getStartShift();
            LocalTime employeeEndShift = employee.getEndShift();

            Duration duration = Duration.between(attendance.getTimeIn(), attendance.getTimeOut());
            BigDecimal totalHours = BigDecimal.valueOf(duration.toMinutes())
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

            // Calculate regular hours based on employee's shift duration
            BigDecimal regularHours = BigDecimal
                    .valueOf(Duration.between(employeeStartShift, employeeEndShift).toMinutes())
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

            BigDecimal overtime = totalHours.subtract(regularHours);
            if (overtime.compareTo(BigDecimal.ZERO) < 0) {
                overtime = BigDecimal.ZERO;
            }

            attendance.setTotalHours(totalHours);
            attendance.setOvertime(overtime);
        }

        return attendanceRepository.save(attendance);
    }

    public Page<Attendance> getAllAttendances(int page, int limit, LocalDate startDate, LocalDate endDate) {
        Pageable pageable = PageRequest.of(page, limit);

        if (startDate != null && endDate == null) {
            return attendanceRepository.findAllByDate(startDate, pageable);
        }

        DateRange dateRange = dateRangeResolver.resolve(startDate, endDate);

        return attendanceRepository.findAllByDateBetween(dateRange.startDate(), dateRange.endDate(), pageable);
    }

    public Page<Attendance> getEmployeeAttendances(int page, int limit, Long employeeId, LocalDate startDate, LocalDate endDate) {
        User user = userService.getAuthenticatedUser();

        String role = user.getUserRole().getRole();
        boolean isAdmin = role.equalsIgnoreCase("HR") || role.equalsIgnoreCase("PAYROLL");
        Long currentEmployeeId = user.getEmployee().getId();

        if (employeeId == null) {
            employeeId = currentEmployeeId;
        }

        if (!isAdmin && !employeeId.equals(currentEmployeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have permission to access this resource");
        }

        Pageable pageable = PageRequest.of(page, limit);
        DateRange dateRange = dateRangeResolver.resolve(startDate, endDate);

        return attendanceRepository.findByEmployee_IdAndDateBetween(employeeId, dateRange.startDate(), dateRange.endDate(), pageable);
    }

    public List<Attendance> getEmployeeAttendances(Long employeeId, LocalDate startDate, LocalDate endDate) {
        return attendanceRepository.findByEmployee_IdAndDateBetween(employeeId, startDate, endDate);
    }

}
