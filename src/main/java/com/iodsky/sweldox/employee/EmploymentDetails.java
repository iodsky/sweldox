package com.iodsky.sweldox.employee;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iodsky.sweldox.common.BaseModel;
import com.iodsky.sweldox.organization.Department;
import com.iodsky.sweldox.organization.Position;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "employment_details")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmploymentDetails extends BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "employee_id")
    @JsonIgnore
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "supervisor_id", nullable = true)
    private Employee supervisor;

    @ManyToOne
    @JoinColumn(name = "position_id")
    private Position position;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(nullable = false)
    private LocalTime startShift;

    @Column(nullable = false)
    private LocalTime endShift;
}
