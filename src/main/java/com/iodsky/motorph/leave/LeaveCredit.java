package com.iodsky.motorph.leave;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iodsky.motorph.employee.model.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "leave_balance")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LeaveCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    @JsonIgnore
    private Employee employee;

    @Enumerated(value = EnumType.STRING)
    private LeaveType type;

    @Min(value = 0, message = "Leave credits cannot be negative")
    private double credits;

    @Version
    private Long version;

}
