package com.temenos.temenosinternship.domain;

import java.util.UUID;

/**
 * Message published to the due timer stream for processing.
 *
 * @param timerId timer identifier
 */
public record DueTimerMessage(UUID timerId) {
}
