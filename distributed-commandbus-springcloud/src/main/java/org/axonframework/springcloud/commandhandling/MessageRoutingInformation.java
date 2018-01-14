/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.springcloud.commandhandling;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Object containing the message routing information required by the
 * {@link org.axonframework.commandhandling.distributed.CommandRouter} to decide to whom an incoming
 * {@link CommandMessage} should be routed.
 * Holds the {@code loadFactor} and {@code commandFilter}, which respectively denote the desired load and set of
 * CommandMessages a node can(not) handle.
 *
 * @author Steven van Beelen
 */
public class MessageRoutingInformation implements Serializable {

    private final int loadFactor;
    private final String serializedCommandFilter;
    private final String serializedCommandFilterType;

    @JsonCreator
    public MessageRoutingInformation(@JsonProperty("loadFactor") int loadFactor,
                                     @JsonProperty("serializedCommandFilter") String serializedCommandFilter,
                                     @JsonProperty("serializedCommandFilterType") String serializedCommandFilterType) {
        this.loadFactor = loadFactor;
        this.serializedCommandFilter = serializedCommandFilter;
        this.serializedCommandFilterType = serializedCommandFilterType;
    }

    public MessageRoutingInformation(int loadFactor, SerializedObject<String> serializedCommandFilter) {
        this(loadFactor, serializedCommandFilter.getData(), serializedCommandFilter.getType().getName());
    }

    public MessageRoutingInformation(int loadFactor,
                                     Predicate<? super CommandMessage<?>> commandFilter,
                                     Serializer serializer) {
        this(loadFactor, serializer.serialize(commandFilter, String.class));
    }

    public int getLoadFactor() {
        return loadFactor;
    }

    public String getSerializedCommandFilter() {
        return serializedCommandFilter;
    }

    public String getSerializedCommandFilterType() {
        return serializedCommandFilterType;
    }

    public Predicate<? super CommandMessage<?>> getCommandFilter(Serializer serializer) {
        SimpleSerializedObject<String> serializedObject = new SimpleSerializedObject<>(
                serializedCommandFilter, String.class, serializedCommandFilterType, null
        );

        return serializer.deserialize(serializedObject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loadFactor, serializedCommandFilter, serializedCommandFilterType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MessageRoutingInformation other = (MessageRoutingInformation) obj;
        return Objects.equals(this.loadFactor, other.loadFactor)
                && Objects.equals(this.serializedCommandFilter, other.serializedCommandFilter)
                && Objects.equals(this.serializedCommandFilterType, other.serializedCommandFilterType);
    }

    @Override
    public String toString() {
        return "MessageRoutingInformation{" +
                "loadFactor=" + loadFactor +
                ", serializedCommandFilter='" + serializedCommandFilter + '\'' +
                ", serializedCommandFilterType='" + serializedCommandFilterType + '\'' +
                '}';
    }
}
