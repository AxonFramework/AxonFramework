/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.queryhandling.distributed;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.queryhandling.QueryHandlerName;
import org.axonframework.queryhandling.QueryMessage;

import java.util.concurrent.CompletableFuture;

/**
 * The {@code QueryBusConnector} interface defines the contract for connecting multiple
 * {@link org.axonframework.queryhandling.QueryBus} instances.
 * <p>
 * It allows for the dispatching of queries across different query bus instances, whether they are local or remote. One
 * connector can be wrapped with another through the {@link DelegatingQueryBusConnector}, upon which more functionality
 * can be added, such as payload conversion or serialization.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface QueryBusConnector extends DescribableComponent {

    /**
     * Subscribes this connector to queries matching the given {@code name}.
     *
     * @param name A combination of the {@link org.axonframework.messaging.QualifiedName} of the
     *             {@link QueryMessage#type()} and {@link QueryMessage#responseType()} to subscribe to.
     * @return A {@code CompletableFuture} that completes successfully when this connector subscribed to the given
     * {@code name}.
     */
    CompletableFuture<Void> subscribe(@Nonnull QueryHandlerName name);

    /**
     * Unsubscribes this connector from queries with the given {@code name}.
     *
     * @param name A combination of the {@link org.axonframework.messaging.QualifiedName} of the
     *             {@link QueryMessage#type()} and {@link QueryMessage#responseType()} to unsubscribe from.
     * @return {@code true} if unsubscribing was successful, {@code false} otherwise.
     */
    boolean unsubscribe(@Nonnull QueryHandlerName name);
}
