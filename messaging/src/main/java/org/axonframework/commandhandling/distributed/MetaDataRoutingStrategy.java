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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;

/**
 * RoutingStrategy implementation that uses the value in the MetaData of a CommandMessage assigned to a given key. The
 * value's {@code toString()} is used to convert the MetaData value to a String.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MetaDataRoutingStrategy extends AbstractRoutingStrategy {

    private final String metaDataKey;

    /**
     * Initializes the MetaDataRoutingStrategy where the given {@code metaDataKey} is used to get the Meta Data
     * value. An error is raised when the MetaData key cannot be found.
     *
     * @param metaDataKey The key on which the value is retrieved from the MetaData.
     */
    public MetaDataRoutingStrategy(String metaDataKey) {
        this(metaDataKey, UnresolvedRoutingKeyPolicy.ERROR);
    }

    /**
     * Initializes the MetaDataRoutingStrategy where the given {@code metaDataKey} is used to get the Meta Data
     * value. The given {@code unresolvedRoutingKeyPolicy} presecribes what to do when the Meta Data properties
     * cannot be found.
     *
     * @param metaDataKey                The key on which the value is retrieved from the MetaData.
     * @param unresolvedRoutingKeyPolicy The policy prescribing behavior when the routing key cannot be resolved
     */
    public MetaDataRoutingStrategy(String metaDataKey, UnresolvedRoutingKeyPolicy unresolvedRoutingKeyPolicy) {
        super(unresolvedRoutingKeyPolicy);
        this.metaDataKey = metaDataKey;
    }

    @Override
    protected String doResolveRoutingKey(CommandMessage<?> command) {
        Object value = command.getMetaData().get(metaDataKey);
        return value == null ? null : value.toString();
    }
}
