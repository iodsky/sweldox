package com.iodsky.motorph.organization;

import com.iodsky.motorph.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;

    public Position getPositionById(String id) {
        return positionRepository.findById(id).orElseThrow(() -> new NotFoundException("Position " + id + " not found"));
    }

}
