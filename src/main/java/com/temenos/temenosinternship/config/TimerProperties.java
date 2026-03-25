package com.temenos.temenosinternship.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for timer scheduling and retry behavior.
 */
@ConfigurationProperties(prefix = "timer")
public class TimerProperties {

    private int longThresholdSeconds = 300;
    private int maxAttempts = 3;
    private int retryBaseDelaySeconds = 30;
    private int loaderBatchSize = 100;
    private long loaderClaimTimeoutMs = 30000;
    private String requestStreamKey = "timer:requests";
    private String dueTimerStreamKey = "timer:due";
    private String createConsumerGroup = "timer-create-group";
    private String processConsumerGroup = "timer-process-group";

    /**
     * Returns the long timer threshold in seconds.
     *
     * @return threshold in seconds
     */
    public int getLongThresholdSeconds() {
        return longThresholdSeconds;
    }

    /**
     * Sets the long timer threshold in seconds.
     *
     * @param longThresholdSeconds threshold in seconds
     */
    public void setLongThresholdSeconds(int longThresholdSeconds) {
        this.longThresholdSeconds = longThresholdSeconds;
    }

    /**
     * Returns the maximum number of processing attempts.
     *
     * @return maximum attempts
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * Sets the maximum number of processing attempts.
     *
     * @param maxAttempts maximum attempts
     */
    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    /**
     * Returns the base retry delay in seconds.
     *
     * @return base retry delay in seconds
     */
    public int getRetryBaseDelaySeconds() {
        return retryBaseDelaySeconds;
    }

    /**
     * Sets the base retry delay in seconds.
     *
     * @param retryBaseDelaySeconds base retry delay in seconds
     */
    public void setRetryBaseDelaySeconds(int retryBaseDelaySeconds) {
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
    }

    /**
     * Returns the loader batch size.
     *
     * @return loader batch size
     */
    public int getLoaderBatchSize() {
        return loaderBatchSize;
    }

    /**
     * Sets the loader batch size.
     *
     * @param loaderBatchSize loader batch size
     */
    public void setLoaderBatchSize(int loaderBatchSize) {
        this.loaderBatchSize = loaderBatchSize;
    }

    /**
     * Returns the long timer loader claim timeout in milliseconds.
     *
     * @return claim timeout in milliseconds
     */
    public long getLoaderClaimTimeoutMs() {
        return loaderClaimTimeoutMs;
    }

    /**
     * Sets the long timer loader claim timeout in milliseconds.
     *
     * @param loaderClaimTimeoutMs claim timeout in milliseconds
     */
    public void setLoaderClaimTimeoutMs(long loaderClaimTimeoutMs) {
        this.loaderClaimTimeoutMs = loaderClaimTimeoutMs;
    }

    /**
     * Returns the request stream key.
     *
     * @return request stream key
     */
    public String getRequestStreamKey() {
        return requestStreamKey;
    }

    /**
     * Sets the request stream key.
     *
     * @param requestStreamKey request stream key
     */
    public void setRequestStreamKey(String requestStreamKey) {
        this.requestStreamKey = requestStreamKey;
    }

    /**
     * Returns the due timer stream key.
     *
     * @return due timer stream key
     */
    public String getDueTimerStreamKey() {
        return dueTimerStreamKey;
    }

    /**
     * Sets the due timer stream key.
     *
     * @param dueTimerStreamKey due timer stream key
     */
    public void setDueTimerStreamKey(String dueTimerStreamKey) {
        this.dueTimerStreamKey = dueTimerStreamKey;
    }

    /**
     * Returns the create consumer group name.
     *
     * @return create consumer group name
     */
    public String getCreateConsumerGroup() {
        return createConsumerGroup;
    }

    /**
     * Sets the create consumer group name.
     *
     * @param createConsumerGroup create consumer group name
     */
    public void setCreateConsumerGroup(String createConsumerGroup) {
        this.createConsumerGroup = createConsumerGroup;
    }

    /**
     * Returns the processing consumer group name.
     *
     * @return processing consumer group name
     */
    public String getProcessConsumerGroup() {
        return processConsumerGroup;
    }

    /**
     * Sets the processing consumer group name.
     *
     * @param processConsumerGroup processing consumer group name
     */
    public void setProcessConsumerGroup(String processConsumerGroup) {
        this.processConsumerGroup = processConsumerGroup;
    }
}
