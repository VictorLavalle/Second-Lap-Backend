package com.secondlap.backend.neighborhood;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NeighborhoodRepository extends JpaRepository<Neighborhood, Long> {

    List<Neighborhood> findAllByOrderByNameAsc();

    Optional<Neighborhood> findByName(String name);
}
