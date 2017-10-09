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

import java.util.function.Predicate;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

/**
 * Object containing the command routing information required by the {@link org.axonframework.commandhandling.distributed.CommandRouter}
 * to decide to whom an incoming {@link CommandMessage} should be routed. Hold the {@code loadFactor} and {@code
 * commandFilter}, which respectively denote the desired load and set of CommandMessages a node can handle.
 */
public class MembershipInformation {

    private final int loadFactor;
    private final String serializedCommandFilter;
    private final String serializedCommandFilterType;

    public MembershipInformation(int loadFactor, String serializedCommandFilter, String serializedCommandFilterType) {
        this.loadFactor = loadFactor;
        this.serializedCommandFilter = serializedCommandFilter;
        this.serializedCommandFilterType = serializedCommandFilterType;
    }

    public MembershipInformation(int loadFactor, SerializedObject<String> serializedCommandFilter) {
        this(loadFactor, serializedCommandFilter.getData(), serializedCommandFilter.getType().getName());
    }

    public MembershipInformation(int loadFactor,
                                 Predicate<? super CommandMessage<?>> commandFilter,
                                 Serializer serializer) {
        this(loadFactor, serializer.serialize(commandFilter, String.class));
    }

    public int getLoadFactor() {
        return loadFactor;
    }

    public Predicate<? super CommandMessage<?>> getCommandFilter(Serializer serializer) {
        SimpleSerializedObject<String> serializedObject = new SimpleSerializedObject<>(
                serializedCommandFilter, String.class, serializedCommandFilterType, null
        );

        return serializer.deserialize(serializedObject);
    }

}
