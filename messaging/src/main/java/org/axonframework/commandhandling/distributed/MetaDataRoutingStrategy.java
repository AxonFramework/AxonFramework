/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.AxonConfigurationException;

import javax.annotation.Nonnull;

/**
 * RoutingStrategy implementation that uses the value in the {@link org.axonframework.messaging.MetaData} of a
 * {@link CommandMessage} assigned to a given key. The value's {@code toString()} is used to convert the
 * {@code MetaData} value to a String.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MetaDataRoutingStrategy implements RoutingStrategy {

    private final String metaDataKey;

    /**
     * Instantiate a {@link MetaDataRoutingStrategy} based on the fields contained in the give {@code builder}.
     * <p>
     * Will assert that the {@code metaDataKey} is not an empty {@link String} or {@code null} and will throw an
     * {@link AxonConfigurationException} if this is the case.
     *
     * @param metaDataKey
     */
    protected MetaDataRoutingStrategy(String metaDataKey) {
        this.metaDataKey = metaDataKey;
    }

    @Override
    public String getRoutingKey(@Nonnull CommandMessage<?> command) {
        Object value = command.getMetaData().get(metaDataKey);
        return value == null ? null : value.toString();
    }
}
