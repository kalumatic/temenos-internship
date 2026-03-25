package com.temenos.temenosinternship.domain;

import java.util.UUID;

/**
 * Serialized request payload sent to the timer request stream.
 */
public record CreateTimerCommand(UUID timerId, long created, int delay) {
}
