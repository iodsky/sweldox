package com.iodsky.sweldox.leave;

import com.iodsky.sweldox.security.user.User;
import com.iodsky.sweldox.security.user.UserService;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveCreditService leaveCreditService;
    private final LeaveRequestMapper leaveRequestMapper;
    private final UserService userService;

    @Transactional
    public LeaveRequest createLeaveRequest(LeaveRequestDto dto) {
        User user = userService.getAuthenticatedUser();
        Long employeeId = user.getEmployee().getId();

        LeaveType type = resolveLeaveType(dto.getLeaveType());

        // validate dates
        validateDates(employeeId, dto);

        // validate leave credits
        double daysRequired = calculateTotalDays(dto.getStartDate(), dto.getEndDate());
        LeaveCredit leaveCredit = leaveCreditService.getLeaveCreditByEmployeeIdAndType(employeeId, type);

        if (daysRequired > leaveCredit.getCredits()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("Insufficient leave credits. Required: %.1f days, Available: %.1f days",
                            daysRequired, leaveCredit.getCredits())
            );
        }

        LeaveRequest leave = LeaveRequest.builder()
                .employee(user.getEmployee())
                .leaveType(type)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .note(dto.getNote())
                .leaveStatus(LeaveStatus.PENDING)
                .build();

        return leaveRequestRepository.save(leave);
    }

    public Page<LeaveRequest> getLeaveRequests(int pageNo, int limit) {
        Pageable page = PageRequest.of(pageNo, limit, Sort.by(Sort.Direction.DESC, "requestDate"));

        User user = userService.getAuthenticatedUser();

        if (user.getUserRole().getRole().equals("HR")) {
            return leaveRequestRepository.findAll(page);
        }

        return leaveRequestRepository.findAllByEmployee_Id(user.getEmployee().getId(), page);
    }

    public LeaveRequest getLeaveRequestById(String leaveRequestId) {
        return leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request " + leaveRequestId + " not found"));
    }

    public LeaveRequest updateLeaveRequest(String leaveRequestId, LeaveRequestDto dto) {
        User user = userService.getAuthenticatedUser();

        LeaveRequest entity = getLeaveRequestById(leaveRequestId);
        if (!entity.getEmployee().getId().equals(user.getEmployee().getId())) {
            if (!user.getUserRole().getRole().equals("HR")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have permission to access this resource");
            }
        }

        if (!entity.getLeaveStatus().equals(LeaveStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete processed leave request");
        }

        LeaveRequest updated = leaveRequestMapper.updateEntity(entity, dto);

        return leaveRequestRepository.save(updated);
    }

    @Transactional
    public LeaveRequest updateLeaveStatus(String leaveRequestId, LeaveStatus newStatus) {
        LeaveRequest leaveRequest = getLeaveRequestById(leaveRequestId);

        if (!leaveRequest.getLeaveStatus().equals(LeaveStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave request " + leaveRequestId + " has already been processed");
        }

        leaveRequest.setLeaveStatus(newStatus);

        if (newStatus.equals(LeaveStatus.APPROVED)) {
            double daysToDeduct = calculateTotalDays(leaveRequest.getStartDate(), leaveRequest.getEndDate());
            LeaveCredit leaveCredit = leaveCreditService.getLeaveCreditByEmployeeIdAndType(leaveRequest.getEmployee().getId(), leaveRequest.getLeaveType());

            if (leaveCredit.getCredits() < daysToDeduct) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        String.format("Cannot approve leave request. Insufficient credits. " +
                                "Required: %.1f days, Available: %.1f days. ",
                                daysToDeduct, leaveCredit.getCredits())
                );
            }

            double newCredits = leaveCredit.getCredits() - daysToDeduct;
            leaveCredit.setCredits(newCredits);

            leaveCreditService.updateLeaveCredit(leaveCredit.getId(), leaveCredit);
        }

        try {
            return leaveRequestRepository.save(leaveRequest);
        } catch (OptimisticLockException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave credits were modified by another process.");
        }
    }

    public void deleteLeaveRequest(String leaveRequestId) {
        User user = userService.getAuthenticatedUser();

        LeaveRequest leaveRequest = getLeaveRequestById(leaveRequestId);
        if (!leaveRequest.getEmployee().getId().equals(user.getEmployee().getId())) {
            if (!user.getUserRole().getRole().equals("HR")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have permission to access this resource");
            }
        }

        if (!leaveRequest.getLeaveStatus().equals(LeaveStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete processed leave request");
        }

        leaveRequestRepository.delete(leaveRequest);
    }

    private double calculateTotalDays(LocalDate startDate, LocalDate endDate) {
        double days = 0;
        LocalDate date = startDate;

        while (!date.isAfter(endDate)) {
            if (!isWeekend(date)) {
                days++;
            }
            date = date.plusDays(1);
        }

        return days;
    }

    private void validateDates(Long employeeId, LeaveRequestDto dto) {
        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range");
        }

        LocalDate startDate = dto.getStartDate();
        if (isWeekend(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be a weekday");
        }

        LocalDate endDate = dto.getEndDate();
        if (isWeekend(endDate)) {
            throw  new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be a weekday");
        }

        // Duplicate dates
        if (leaveRequestRepository.existsByEmployee_IdAndStartDateAndEndDate(
                employeeId, dto.getStartDate(), dto.getEndDate()
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate leave request");
        }

        // Overlapping dates
        if (leaveRequestRepository
                .existsByEmployee_IdAndLeaveStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employeeId,
                        List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED),
                        endDate,
                        startDate
                )
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave request overlaps with an existing pending or approved leave");
        }
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private LeaveType resolveLeaveType(String leaveTypeStr) {
        LeaveType type;
        try {
            type = LeaveType.valueOf(leaveTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid leave type: " + leaveTypeStr);
        }
        return type;
    }

}
