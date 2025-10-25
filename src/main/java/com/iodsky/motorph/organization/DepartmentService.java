package com.iodsky.motorph.organization;

import com.iodsky.motorph.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public Department getDepartmentById(String id) {
        return departmentRepository.findById(id).orElseThrow(() -> new NotFoundException("Department " + id + " not found"));
    }
}
