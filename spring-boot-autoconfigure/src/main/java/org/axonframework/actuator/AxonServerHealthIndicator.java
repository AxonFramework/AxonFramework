/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.actuator;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

/**
 * An {@link AbstractHealthIndicator} implementation exposing the health of the connections made through the {@link
 * AxonServerConnectionManager}. Shares information per connected context under {@code
 * "{context-name}.connection.active"}.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class AxonServerHealthIndicator extends AbstractHealthIndicator {

    private static final String CONNECTION = "%s.connection.active";

    private final AxonServerConnectionManager connectionManager;

    /**
     * Constructs this health indicator, extracting health information from the given {@code connectionManager}.
     *
     * @param connectionManager The Axon Server connection manager to extract health information from.
     */
    public AxonServerHealthIndicator(AxonServerConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        connectionManager.connections()
                         .forEach((key, value) -> builder.withDetail(String.format(CONNECTION, key), value));
    }
}
