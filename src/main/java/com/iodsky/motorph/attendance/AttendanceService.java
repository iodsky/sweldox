package com.iodsky.motorph.attendance;

import com.iodsky.motorph.common.DateRange;
import com.iodsky.motorph.common.DateRangeResolver;
import com.iodsky.motorph.common.exception.*;
import com.iodsky.motorph.employee.EmployeeService;
import com.iodsky.motorph.employee.model.Employee;
import com.iodsky.motorph.security.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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

    private final LocalTime START_SHIFT = LocalTime.of(8, 0);
    private final LocalTime END_SHIFT = LocalTime.of(17, 0);
    private final LocalTime EARLIEST_START_SHIFT = START_SHIFT.minusMinutes(15);

    private final AttendanceRepository attendanceRepository;
    private final EmployeeService employeeService;
    private final DateRangeResolver dateRangeResolver;

    public Attendance createAttendance(AttendanceDto attendanceDto) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof User user)) {
            throw new UnauthorizedException("Authentication required to access this resource");
        }

        // Determine role flags
        String role = user.getUserRole().getRole().toUpperCase();
        boolean isHr = "HR".equals(role);

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
                throw new ForbiddenException("You don't have the permissions to access this resource");
            }
        }

        // Determine attendance date and time
        LocalDate attendanceDate = (attendanceDto != null && attendanceDto.getDate() != null)
                ? attendanceDto.getDate()
                : LocalDate.now();

        LocalTime clockInTime = (attendanceDto != null && attendanceDto.getTimeIn() != null)
                ? attendanceDto.getTimeIn()
                : LocalTime.now();

        // Prevent early clock-in
        if (clockInTime.isBefore(EARLIEST_START_SHIFT)) {
            throw new BadRequestException("Cannot clock in before " + EARLIEST_START_SHIFT);
        }

        // Check for existing attendance record
        Optional<Attendance> exists = attendanceRepository.findByEmployee_IdAndDate(employeeId, attendanceDate);
        if (exists.isPresent()) {
            throw new ConflictException("Attendance already exists");
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
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof User user)) {
            throw new UnauthorizedException("Authentication required to access this resource");
        }

        boolean isHr = "HR".equalsIgnoreCase(user.getUserRole().getRole());

        Attendance attendance = attendanceRepository.findById(id)
                // Not yet clocked in
                .orElseThrow(() -> new NotFoundException("Attendance not found with id: " + id));

        long currentEmpId = user.getEmployee().getId();
        long employeeId = attendance.getEmployee().getId();

        if (!isHr && employeeId != currentEmpId) {
            throw new ForbiddenException("You don't have the permissions to access this resource");
        }

        if (attendanceDto == null) {
            if (attendance.getTimeOut() != null && !attendance.getTimeOut().equals(LocalTime.MIN)) {
                throw new ConflictException("You have already clocked out for the day.");
            }

            LocalTime clockOut = LocalTime.now();
            if (clockOut.isBefore(attendance.getTimeIn())) {
                throw new BadRequestException("Clock-out time cannot be before clock-in time");
            }

            attendance.setTimeOut(clockOut);
        }
        else {
            if (!isHr) {
                throw new ForbiddenException("You don't have the permissions to access this resource");
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
            Duration duration = Duration.between(attendance.getTimeIn(), attendance.getTimeOut());
            BigDecimal totalHours = BigDecimal.valueOf(duration.toMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

            BigDecimal regularHours = BigDecimal
                    .valueOf(Duration.between(START_SHIFT, END_SHIFT).toMinutes())
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

    public List<Attendance> getAllAttendances(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate == null) {
            return attendanceRepository.findAllByDate(startDate);
        }

        DateRange dateRange = dateRangeResolver.resolve(startDate, endDate);

        return attendanceRepository.findAllByDateBetween(dateRange.startDate(), dateRange.endDate());
    }

    public List<Attendance> getEmployeeAttendances(Long employeeId, LocalDate startDate, LocalDate endDate) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof User user)) {
            throw new UnauthorizedException("Authentication required to access this resource");
        }

        String role = user.getUserRole().getRole();
        boolean isAdmin = role.equalsIgnoreCase("HR") || role.equalsIgnoreCase("PAYROLL");
        Long currentEmployeeId = user.getEmployee().getId();

        if (employeeId == null) {
            employeeId = currentEmployeeId;
        }

        if (!isAdmin && !employeeId.equals(currentEmployeeId)) {
            throw new ForbiddenException("You don't have permission to access this resource");
        }

        DateRange dateRange = dateRangeResolver.resolve(startDate, endDate);

        return attendanceRepository.findByEmployee_IdAndDateBetween(employeeId, dateRange.startDate(), dateRange.endDate());
    }

}
