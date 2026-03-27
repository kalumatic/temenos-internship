package com.temenos.temenosinternship.mapper;

import com.temenos.temenosinternship.domain.TimerEntity;
import com.temenos.temenosinternship.model.Timer;
import org.springframework.stereotype.Component;

/**
 * Maps persistence objects to API models.
 */
@Component
public class TimerMapper {

    /**
     * Converts a timer entity into an API timer model.
     *
     * @param entity timer entity
     * @return API timer model
     */
    public Timer toModel(TimerEntity entity) {
        return new Timer()
            .timerId(entity.getTimerId().toString())
            .created(entity.getCreated())
            .delay(entity.getDelay())
            .callbackUrl(entity.getCallbackUrl())
            .csrfToken(entity.getCsrfToken())
            .status(com.temenos.temenosinternship.model.TimerStatus.fromValue(entity.getStatus().name()))
            .attempts(entity.getAttempts());
    }
}
