package com.temenos.temenosinternship.domain;

/**
 * Represents the current lifecycle state of a timer.
 */
public enum TimerStatus {
    PENDING,
    LOADING,
    SCHEDULED,
    READY,
    PROCESSING,
    COMPLETED,
    FAILED
}
