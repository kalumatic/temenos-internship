package com.temenos.temenosinternship.repository;

import com.temenos.temenosinternship.domain.TimerEntity;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * Reactive CRUD repository for timers.
 */
public interface TimerRepository extends ReactiveCrudRepository<TimerEntity, UUID>, TimerCustomRepository {
}
