package com.iodsky.motorph.organization;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "position")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Position {

    @Id
    @Column(length = 20)
    private String id;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false, unique = true)
    private String title;
}
