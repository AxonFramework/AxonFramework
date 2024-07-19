/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.springboot.service.connection;

import org.axonframework.axonserver.connector.AxonServerConfiguration;

/**
 * An {@link AxonServerConnectionDetails} implementation based on a given {@link AxonServerConfiguration} bean.
 * <p>
 * Used to ensure the properties from an {@code ServiceConnection} annotated test container take precedence over
 * property-based configuration, as this would trigger the {@link AxonServerTestContainerConnectionDetailsFactory} to
 * construct a {@link AxonServerConnectionDetails} object first. Due to this ordering, the property-based format,
 * inserted through this class, is no longer able to override the properties, like the
 * {@link AxonServerConfiguration#getServers()}.
 *
 * @author Steven van Beelen
 * @since 4.9.4
 */
public class PropertiesAxonServerConnectionDetails implements AxonServerConnectionDetails {

    private final String routingServers;

    /**
     * Constructs a {@link PropertiesAxonServerConnectionDetails} instance based on the given {@code configuration}.
     *
     * @param configuration An {@link AxonServerConfiguration} to base this
     *                      {@link PropertiesAxonServerConnectionDetails} instance on.
     */
    public PropertiesAxonServerConnectionDetails(AxonServerConfiguration configuration) {
        this.routingServers = configuration.getServers();
    }

    @Override
    public String routingServers() {
        return routingServers;
    }
}
