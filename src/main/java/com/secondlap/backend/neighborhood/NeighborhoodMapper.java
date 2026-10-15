package com.secondlap.backend.neighborhood;

import com.secondlap.backend.neighborhood.dto.NeighborhoodResponse;
import org.springframework.stereotype.Component;

@Component
public class NeighborhoodMapper {

    public NeighborhoodResponse toResponse(Neighborhood neighborhood) {
        return new NeighborhoodResponse(neighborhood.getName(), neighborhood.getFee());
    }
}
