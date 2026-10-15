package com.secondlap.backend.neighborhood;

import com.secondlap.backend.neighborhood.dto.NeighborhoodResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/neighborhoods")
public class NeighborhoodController {

    private final NeighborhoodService neighborhoodService;

    public NeighborhoodController(NeighborhoodService neighborhoodService) {
        this.neighborhoodService = neighborhoodService;
    }

    @GetMapping
    public List<NeighborhoodResponse> getNeighborhoods() {
        return neighborhoodService.findAll();
    }
}
