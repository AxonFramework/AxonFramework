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

/**
 * Holds information about connection property.
 *
 * @author Milan Savic
 * @since 4.2
 */
public class ConnectionProperty {

    /**
     * The value of the property.
     */
    private String value;
    /**
     * Whether Axon Server Node is required to have this property.
     */
    private boolean required;
    /**
     * How important this property is. Greater values indicate more important properties.
     */
    private int weight;

    /**
     * Gets the value of this property.
     *
     * @return the value of this property
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value of this property.
     *
     * @param value the value of this property
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Indicates whether this property is required on Axon Server Node.
     *
     * @return {@code true} if this property is required on Axon Server Node, {@code false} otherwise
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Sets whether this property is required on Axon Server Node.
     *
     * @param required whether this property is required on Axon Server Node
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * Gets weight of this property.
     *
     * @return the weight of this property
     */
    public int getWeight() {
        return weight;
    }

    /**
     * Sets the weight of this property
     *
     * @param weight the weight of this property
     */
    public void setWeight(int weight) {
        this.weight = weight;
    }

    /**
     * Converts this Connection Property  to gRPC message to be sent to the Axon Server.
     *
     * @return gRPC message to be sent to the Axon Server
     */
    public io.axoniq.axonserver.grpc.control.ConnectionProperty convert() {
        return io.axoniq.axonserver.grpc.control.ConnectionProperty.newBuilder()
                                                                   .setValue(value)
                                                                   .setRequired(required)
                                                                   .setWeight(weight)
                                                                   .build();
    }
}
