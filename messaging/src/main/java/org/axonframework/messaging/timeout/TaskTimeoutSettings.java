package org.axonframework.messaging.timeout;

/**
 * Configuration properties for a task timeout. Used in other parts of the configuration.
 *
 * @author Mitchell Herrijgers
 * @since 4.11.0
 */
public class TaskTimeoutSettings {

    /**
     * The timeout of the message handler in milliseconds.
     */
    private int timeoutMs = -1;
    /**
     * The threshold in milliseconds after which a warning is logged. Setting this to a value higher than
     * {@code timeout} will disable warnings.
     */
    private int warningThresholdMs = -1;

    /**
     * The interval in milliseconds between warnings.
     */
    private int warningIntervalMs = -1;

    /**
     * Creates a new {@link TaskTimeoutSettings} with the provided timeout settings.
     *
     * @param timeoutMs          the timeout in milliseconds
     * @param warningThresholdMs the threshold in milliseconds after which a warning is logged. Setting this to a value
     *                           higher than {@code timeout} will disable warnings.
     * @param warningIntervalMs  the interval in milliseconds between warnings
     */
    public TaskTimeoutSettings(int timeoutMs, int warningThresholdMs, int warningIntervalMs) {
        this.timeoutMs = timeoutMs;
        this.warningThresholdMs = warningThresholdMs;
        this.warningIntervalMs = warningIntervalMs;
    }

    /**
     * Creates a new {@link TaskTimeoutSettings} with default timeout settings.
     * This means all timeouts are disabled.
     */
    public TaskTimeoutSettings() {
    }

    /**
     * Returns the timeout of the message handler in milliseconds.
     *
     * @return the timeout of the message handler in milliseconds
     */
    public int getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * Sets the timeout of the message handler in milliseconds.
     *
     * @param timeoutMs the timeout of the message handler in milliseconds
     */
    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Returns the threshold in milliseconds after which a warning is logged. Setting this to a value higher than
     * {@code timeout} will disable warnings.
     *
     * @return the threshold in milliseconds after which a warning is logged
     */
    public int getWarningThresholdMs() {
        return warningThresholdMs;
    }

    /**
     * Sets the threshold in milliseconds after which a warning is logged. Setting this to a value higher than
     * {@code timeout} will disable warnings.
     *
     * @param warningThresholdMs the threshold in milliseconds after which a warning is logged
     */
    public void setWarningThresholdMs(int warningThresholdMs) {
        this.warningThresholdMs = warningThresholdMs;
    }

    /**
     * Returns the interval in milliseconds between warnings.
     *
     * @return the interval in milliseconds between warnings
     */
    public int getWarningIntervalMs() {
        return warningIntervalMs;
    }

    /**
     * Sets the interval in milliseconds between warnings.
     *
     * @param warningIntervalMs the interval in milliseconds between warnings
     */
    public void setWarningIntervalMs(int warningIntervalMs) {
        this.warningIntervalMs = warningIntervalMs;
    }
}
