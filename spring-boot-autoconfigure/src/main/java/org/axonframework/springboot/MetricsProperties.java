/*
 * Copyright (c) 2010-2018. Axon Framework
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

/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.springboot;

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
     * Retrieves the AutoConfiguration settings for Metrics
     *
     * @return the AutoConfiguration settings for Metrics
     */
    public AutoConfiguration getAutoConfiguration() {
        return autoConfiguration;
    }

    /**
     * Defines the AutoConfiguration settings for Metrics.
     *
     * @param autoConfiguration the AutoConfiguration settings for Metrics.
     */
    public void setAutoConfiguration(AutoConfiguration autoConfiguration) {
        this.autoConfiguration = autoConfiguration;
    }

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
