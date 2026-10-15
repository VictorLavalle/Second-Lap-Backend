package com.secondlap.backend.neighborhood;

import com.secondlap.backend.neighborhood.dto.NeighborhoodResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class NeighborhoodService {

    private final NeighborhoodRepository neighborhoodRepository;
    private final NeighborhoodMapper neighborhoodMapper;

    public NeighborhoodService(NeighborhoodRepository neighborhoodRepository,
                               NeighborhoodMapper neighborhoodMapper) {
        this.neighborhoodRepository = neighborhoodRepository;
        this.neighborhoodMapper = neighborhoodMapper;
    }

    public List<NeighborhoodResponse> findAll() {
        return neighborhoodRepository.findAllByOrderByNameAsc().stream()
                .map(neighborhoodMapper::toResponse)
                .toList();
    }
}
