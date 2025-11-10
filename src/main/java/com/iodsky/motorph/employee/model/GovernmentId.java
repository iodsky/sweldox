package com.iodsky.motorph.employee.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "government_id")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GovernmentId {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "employee_id")
    @JsonIgnore
    private Employee employee;

    @Column(name = "sss_no", unique = true)
    private String sssNumber;

    @Column(name = "tin_no", unique = true)
    private String tinNumber;

    @Column(name = "philhealth_no", unique = true)
    private String philhealthNumber;

    @Column(name = "pagibig_no", unique = true)
    private String pagIbigNumber;
}
