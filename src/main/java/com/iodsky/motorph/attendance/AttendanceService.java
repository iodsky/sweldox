package com.iodsky.motorph.attendance;

import com.iodsky.motorph.common.exception.NotFoundException;
import com.iodsky.motorph.employee.EmployeeService;
import com.iodsky.motorph.employee.model.Employee;
import com.iodsky.motorph.security.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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

    public Attendance createAttendance(AttendanceDto attendanceDto) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof User user)) {
            throw new RuntimeException("Unauthorized access");
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
                throw new IllegalArgumentException("Only HR can create attendance for another employee");
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
            throw new IllegalArgumentException("Cannot clock in before " + EARLIEST_START_SHIFT);
        }

        // Check for existing attendance record
        Optional<Attendance> exists = attendanceRepository.findByEmployee_IdAndDate(employeeId, attendanceDate);
        if (exists.isPresent()) {
            throw new IllegalArgumentException("Attendance already exists for " + attendanceDate);
        }

        // Build attendance
        Employee employee = employeeService.getEmployeeById(employeeId);

        Attendance attendance = Attendance.builder()
                .employee(employee)
                .date(attendanceDate)
                .timeIn(clockInTime)
                .timeOut(LocalTime.MIN)
                .totalHours(0.0)
                .overtime(0.0)
                .build();

        return attendanceRepository.save(attendance);
    }

    public Attendance updateAttendance(UUID id, AttendanceDto attendanceDto) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof User user)) {
            throw new RuntimeException("Unauthorized: invalid user context");
        }

        boolean isHr = "HR".equalsIgnoreCase(user.getUserRole().getRole());

        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Attendance not found with id: " + id));

        long currentEmpId = user.getEmployee().getId();
        long employeeId = attendance.getEmployee().getId();

        if (!isHr && employeeId != currentEmpId) {
            throw new RuntimeException("You don't have permission to perform this operation.");
        }

        if (attendanceDto == null) {
            if (attendance.getTimeOut() != null && !attendance.getTimeOut().equals(LocalTime.MIN)) {
                throw new IllegalArgumentException("You have already clocked out for the day.");
            }

            LocalTime clockOut = LocalTime.now();
            if (clockOut.isBefore(attendance.getTimeIn())) {
                throw new IllegalArgumentException("Clock-out time cannot be before clock-in time");
            }

            attendance.setTimeOut(clockOut);
        }
        else {
            if (!isHr) {
                throw new RuntimeException("You don't have permission to perform this operation");
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
            double totalHours = duration.toMinutes() / 60.0;

            double regularHours = Duration.between(START_SHIFT, END_SHIFT).toMinutes() / 60.0;
            double overtime = Math.max(totalHours - regularHours, 0);

            attendance.setTotalHours(totalHours);
            attendance.setOvertime(overtime);
        }

        return attendanceRepository.save(attendance);
    }

    public List<Attendance> getAllAttendances(LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(startDate, endDate);

        return attendanceRepository.findAllByDateBetween(dateRange.startDate(), dateRange.endDate());
    }

    public List<Attendance> getEmployeeAttendances(Long employeeId, LocalDate startDate, LocalDate endDate) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof User user)) {
            throw new RuntimeException("Unauthorized");
        }

        boolean isHr = "HR".equalsIgnoreCase(user.getUserRole().getRole());
        Long currentEmployeeId = user.getEmployee().getId();

        if (employeeId == null) {
            employeeId = currentEmployeeId;
        }

        if (!isHr && !employeeId.equals(currentEmployeeId)) {
            throw new RuntimeException("You don't have the permissions to perform this operation");
        }

        DateRange dateRange = resolveDateRange(startDate, endDate);

        return attendanceRepository.findByEmployee_IdAndDateBetween(employeeId, dateRange.startDate, dateRange.endDate);
    }

    private DateRange resolveDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now();
        if (endDate == null) endDate = startDate.plusDays(15);
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }
        return new DateRange(startDate, endDate);
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) { }
}
