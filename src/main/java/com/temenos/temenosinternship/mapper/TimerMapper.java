package com.temenos.temenosinternship.mapper;

import com.temenos.temenosinternship.domain.TimerEntity;
import com.temenos.temenosinternship.model.Timer;

/**
 * Maps persistence objects to API models.
 */
public interface TimerMapper {

    /**
     * Converts a timer entity into an API timer model.
     *
     * @param entity timer entity
     * @return API timer model
     */
    Timer toModel(TimerEntity entity);
}
