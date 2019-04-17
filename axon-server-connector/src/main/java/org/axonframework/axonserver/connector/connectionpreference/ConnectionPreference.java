/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.axonserver.connector.connectionpreference;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents preference toward connecting to Axon Server Node.
 *
 * @author Milan Savic
 * @since 4.2
 */
public class ConnectionPreference {

    /**
     * Connection properties map. Key is the name of the property.
     */
    private Map<String, ConnectionProperty> properties = new LinkedHashMap<>();

    /**
     * Gets connection properties.
     *
     * @return a map of connection properties where key the name of the property
     */
    public Map<String, ConnectionProperty> getProperties() {
        return properties;
    }

    /**
     * Sets the connection properties.
     *
     * @param properties the connection property map where the key is the name of the property
     */
    public void setProperties(Map<String, ConnectionProperty> properties) {
        this.properties = properties;
    }

    /**
     * Adds a property with given {@code name}.
     *
     * @param name     the name of the property
     * @param property the actual property
     */
    public void addProperty(String name, ConnectionProperty property) {
        properties.put(name, property);
    }

    /**
     * Converts this Connection Preference to gRPC message to be sent to the Axon Server.
     *
     * @return gRPC message to be sent to the Axon Server
     */
    public io.axoniq.axonserver.grpc.control.ConnectionPreference convert() {
        return io.axoniq.axonserver.grpc.control.ConnectionPreference.newBuilder()
                                                                     .putAllProperties(convertToGrpcProperties())
                                                                     .build();
    }

    private Map<String, io.axoniq.axonserver.grpc.control.ConnectionProperty> convertToGrpcProperties() {
        return properties.entrySet()
                         .stream()
                         .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().convert()));
    }
}
