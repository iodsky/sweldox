package com.iodsky.motorph.leave;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iodsky.motorph.employee.model.Employee;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;

@Entity
@Table(
        name = "leave_request",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"employee_id", "startDate", "endDate"}
        )
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LeaveRequest {

    @Id
    @LeaveRequestId
    private String id;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    @JsonIgnore
    private Employee employee;

    @Enumerated(EnumType.STRING)
    private LeaveType leaveType;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDate requestDate;

    private LocalDate startDate;

    private LocalDate endDate;

    private String note;

    @Enumerated(value = EnumType.STRING)
    private LeaveStatus leaveStatus;

    @Version
    private Long version;

}
