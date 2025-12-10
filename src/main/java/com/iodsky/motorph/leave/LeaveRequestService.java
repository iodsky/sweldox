package com.iodsky.motorph.leave;

import com.iodsky.motorph.common.exception.BadRequestException;
import com.iodsky.motorph.common.exception.NotFoundException;
import com.iodsky.motorph.common.exception.UnauthorizedException;
import com.iodsky.motorph.security.user.User;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveCreditService leaveCreditService;

    @Transactional
    public LeaveRequest createLeaveRequest(LeaveRequestDto dto) {

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof User user)) {
            throw new UnauthorizedException("Authentication error");
        }
        Long employeeId = user.getEmployee().getId();

        LeaveType type = resolveLeaveType(dto.getLeaveType());

        // validate dates
        validateDates(employeeId, dto);

        // validate leave credits
        double daysRequired = calculateTotalDays(dto.getStartDate(), dto.getEndDate());
        LeaveCredit leaveCredit = leaveCreditService.getLeaveCreditByEmployeeIdAndType(employeeId, type);

        if (daysRequired > leaveCredit.getCredits()) {
            throw new BadRequestException(
                    String.format("Insufficient leave credits. Required: %.1f days, Available: %.1f days",
                            daysRequired, leaveCredit.getCredits())
            );
        }

        LeaveRequest leave = LeaveRequest.builder()
                .employee(user.getEmployee())
                .leaveType(type)
                .requestDate(LocalDate.now())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .note(dto.getNote())
                .leaveStatus(LeaveStatus.PENDING)
                .build();

        return leaveRequestRepository.save(leave);
    }

    public Page<LeaveRequest> getLeaveRequests(int pageNo, int limit) {
        Pageable page = PageRequest.of(pageNo, limit, Sort.by(Sort.Direction.DESC, "requestDate"));

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof User user)) {
            throw new UnauthorizedException("Authentication error");
        }

        if (user.getUserRole().getRole().equals("HR")) {
            return leaveRequestRepository.findAll(page);
        }

        return leaveRequestRepository.findAllByEmployee_Id(user.getEmployee().getId(), page);
    }

    @Transactional
    public LeaveRequest updateLeaveStatus(String leaveRequestId, LeaveStatus newStatus) {
        LeaveRequest leaveRequest = getLeaveRequestById(leaveRequestId);

        if (!leaveRequest.getLeaveStatus().equals(LeaveStatus.PENDING)) {
            throw new BadRequestException("Leave request " + leaveRequestId + " has already been processed");
        }

        leaveRequest.setLeaveStatus(newStatus);

        if (newStatus.equals(LeaveStatus.APPROVED)) {
            double daysToDeduct = calculateTotalDays(leaveRequest.getStartDate(), leaveRequest.getEndDate());
            LeaveCredit leaveCredit = leaveCreditService.getLeaveCreditByEmployeeIdAndType(leaveRequest.getEmployee().getId(), leaveRequest.getLeaveType());

            if (leaveCredit.getCredits() < daysToDeduct) {
                throw new BadRequestException(
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
            throw new BadRequestException("Leave credits were modified by another process.");
        }
    }

    public LeaveRequest getLeaveRequestById(String leaveRequestId) {
        return leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new NotFoundException("Leave request " + leaveRequestId + " not found"));
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
            throw new BadRequestException("Invalid date range");
        }

        LocalDate startDate = dto.getStartDate();
        if (isWeekend(startDate)) {
            throw new BadRequestException("Start date must be a weekday");
        }

        LocalDate endDate = dto.getEndDate();
        if (isWeekend(endDate)) {
            throw  new BadRequestException("End date must be a weekday");
        }

        // Duplicate dates
        if (leaveRequestRepository.existsByEmployee_IdAndStartDateAndEndDate(
                employeeId, dto.getStartDate(), dto.getEndDate()
        )) {
            throw new BadRequestException("Duplicate leave request");
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
            throw new BadRequestException("Leave request overlaps with an existing pending or approved leave");
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
            throw new BadRequestException("Invalid leave type: " + leaveTypeStr);
        }
        return type;
    }

}
