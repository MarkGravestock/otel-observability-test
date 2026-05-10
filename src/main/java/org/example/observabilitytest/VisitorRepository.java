package org.example.observabilitytest;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface VisitorRepository extends CrudRepository<VisitorCounter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VisitorCounter> findById(Long id);
}
