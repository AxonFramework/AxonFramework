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
 * Ensures there will be a {@code AxonServerConnectionDetails} instance in the absence of the container-based version
 * constructed by the {@link AxonServerTestContainerConnectionDetailsFactory}. The container-based version only exists
 * if there is a {@code ServiceConnection} annotated bean in the context, which is not a guarantee.
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
