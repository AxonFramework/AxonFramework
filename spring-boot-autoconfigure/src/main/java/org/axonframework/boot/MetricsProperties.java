package org.axonframework.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties describing the settings for Metrics.
 *
 * @author Steven van Beelen
 * @since 3.2
 */
@ConfigurationProperties("axon.metrics")
public class MetricsProperties {

    private AutoConfiguration autoConfiguration = new AutoConfiguration();

    /**
     * Auto configuration specific properties around Metrics.
     */
    public static class AutoConfiguration {

        /**
         * Enables Metrics auto configuration for this application
         */
        private boolean enabled = true;

        /**
         * Indicates whether the auto-configuration of Metrics is enabled
         *
         * @return true if the auto-configuration of Metrics is enabled, false if otherwise
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables (if {@code true}, default) or disables (if {@code false}) the auto-configuration of Metrics within
         * the application context.
         *
         * @param enabled whether to enable Metrics auto configuration
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
