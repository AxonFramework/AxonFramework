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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.Context.ResourceKey;

import javax.annotation.Nonnull;

/**
 * Interface describing a mechanism that generates a routing key for a given command. Commands that should be handled by
 * the same segment, should result in the same routing key.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface RoutingStrategy {

    ResourceKey<String> ROUTING_KEY = ResourceKey.create("RoutingKey");

    /**
     * Generates a routing key for the given {@code command}. Commands that should be handled by the same segment,
     * should result in the same routing key.
     *
     * @param command the command to create a routing key for
     * @return the routing key for the command
     */
    String getRoutingKey(@Nonnull CommandMessage<?> command);
}
