/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extension.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties describing the settings for metrics.
 *
 * @author Steven van Beelen
 * @since 3.2.0
 */
@ConfigurationProperties("axon.metrics")
public class MetricsProperties {

    /**
     * Indicates whether the metrics are enabled, defaulting to {@code true}.
     */
    private boolean enabled = true;

    /**
     * Properties specific for use with Micrometer.
     */
    private Micrometer micrometer = new Micrometer();

    /**
     * Indicates whether the auto-configuration of metrics is enabled.
     *
     * @return {@code true} if the auto-configuration of metrics is enabled, {@code false} if otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables (if {@code true}, default) or disables (if {@code false}) the auto-configuration of metrics within the
     * application context.
     *
     * @param enabled whether to enable metrics autoconfiguration
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Retrieves the Micrometer specific settings for metrics.
     *
     * @return the Micrometer settings for metrics
     */
    public Micrometer getMicrometer() {
        return micrometer;
    }

    /**
     * Defines the Micrometer settings for metrics.
     *
     * @param micrometer the Micrometer settings for metrics
     */
    public void setMicrometer(Micrometer micrometer) {
        this.micrometer = micrometer;
    }

    /**
     * Properties specific for usage with Micrometer.
     */
    public static class Micrometer {

        /**
         * Enables Micrometer tags for this application.
         */
        private boolean dimensional = false;

        /**
         * Indicates whether the Micrometer tags/dimensions will be used
         *
         * @return {@code true} if the Micrometer tags/dimensions will be used, {@code false} if otherwise
         */
        public boolean isDimensional() {
            return dimensional;
        }

        /**
         * Disables (if {@code false}, default) or enables (if {@code true}) the usage of Micrometer tags/dimensions.
         *
         * @param dimensional whether the Micrometer tags/dimensions will be used
         */
        public void setDimensional(boolean dimensional) {
            this.dimensional = dimensional;
        }
    }
}
