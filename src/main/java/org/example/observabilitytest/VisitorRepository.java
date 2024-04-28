package org.example.observabilitytest;

import org.springframework.data.repository.CrudRepository;

public interface VisitorRepository extends CrudRepository<VisitorCounter, Long> {
}
