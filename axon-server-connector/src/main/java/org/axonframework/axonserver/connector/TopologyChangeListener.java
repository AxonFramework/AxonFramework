/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.axonserver.connector;

import java.util.function.Consumer;

/**
 * A functional interface representing a listener that is notified when a {@link TopologyChange} occurs to a specific
 * {@link AxonServerConfiguration#getContext() context}.
 * <p>
 * A {@code TopologyChange} can reflect any of the following {@link TopologyChange#type() update}:
 * <ol>
 *     <li>{@link org.axonframework.axonserver.connector.TopologyChange.Type#COMMAND_HANDLER_ADDED} - A <b>command</b> handler was added by a specific instance to the context the listener is attached to.</li>
 *     <li>{@link org.axonframework.axonserver.connector.TopologyChange.Type#COMMAND_HANDLER_REMOVED} - A <b>command</b> handler was removed from a specific instance for the context the listener is attached to.</li>
 *     <li>{@link org.axonframework.axonserver.connector.TopologyChange.Type#QUERY_HANDLER_ADDED} - A <b>query</b> handler was added by a specific instance to the context the listener is attached to.</li>
 *     <li>{@link org.axonframework.axonserver.connector.TopologyChange.Type#QUERY_HANDLER_REMOVED} - A <b>query</b> handler was removed from a specific instance for the context the listener is attached to.</li>
 *     <li>{@link org.axonframework.axonserver.connector.TopologyChange.Type#RESET} - This signals the connection broke down of a specific instance for the context the listener is attached to.</li>
 * </ol>
 * <p>
 * A {@code TopologyChange} includes more information to better comprehend the actual change, being:
 * <ul>
 *     <li>{@link TopologyChange#type() The type} - The type of update.</li>
 *     <li>{@link TopologyChange#context() The context} - The context from which the change originates.</li>
 *     <li>{@link TopologyChange#clientId() The client id} - The {@link AxonServerConfiguration#getClientId() identifier of the client} for which a topology change occurred.</li>
 *     <li>{@link TopologyChange#clientStreamId() The client stream id} - The identifier of the stream/connection for a client (e.g., the Axon Framework node) for which a topology change occurred, defined by Axon Server.</li>
 *     <li>{@link TopologyChange#componentName() The component name} - The {@link AxonServerConfiguration#getComponentName() name of the component} for which a topology change occurred.</li>
 *     <li>{@link TopologyChange#handler() The handler subscription} - The handler subscription containing the {@link TopologyChange.HandlerSubscription#name()} and an optional {@link TopologyChange.HandlerSubscription#loadFactor()} that is only present for command handler subscriptions.</li>
 * </ul>
 * Here is an example {@link TopologyChange#toString()} for a {@link org.axonframework.axonserver.connector.TopologyChange.Type#COMMAND_HANDLER_ADDED}:
 * <pre>{@code
 * TopologyChange{
 *     type=COMMAND_HANDLER_ADDED,
 *     context='axoniq-university',
 *     clientId='149466@axoniq-uni',
 *     clientStreamId='149466@axoniq-uni.2bb5c0b4-59d9-4396-ad14-a32059f71d9a',
 *     componentName='AxonIQ University',
 *     handlerSubscription=HandlerSubscription{
 *         name='io.axoniq.demo.university.faculty.write.createcourse.CreateCourse',
 *         loadFactor=100
 *     }
 * }
 * }</pre>
 * <p>
 * An example use case of this functional interface, is to get notified when a new instance connects or disconnects. A
 * newly connecting instance would mean a new {@link TopologyChange#clientStreamId()} entered, while a disconnect is
 * signaled by the {@link TopologyChange.Type#RESET} {@link TopologyChange#type()}. Such a coarse topology change means the
 * routing of commands will change, which impacts {@link org.axonframework.common.caching.Cache} hits for your
 * aggregates or sagas, for example.
 * <p>
 * Registering this interface with the {@link org.axonframework.config.Configurer} will ensure it is registered with the
 * {@link AxonServerConfiguration#getContext() default context} <b>only</b>! If you need to register several change
 * listeners, either with the same context or with different contexts, you are required to retrieve the
 * {@link io.axoniq.axonserver.connector.control.ControlChannel} from the {@code AxonServerConnectionManager} manually,
 * and invoke {@link io.axoniq.axonserver.connector.control.ControlChannel#registerTopologyChangeHandler(Consumer)} for
 * each change listener separately.
 * <p>
 * Note that Axon Server 2025.1.2 or up is required to use this feature!
 *
 * @author Steven van Beelen
 * @since 4.12.0
 */
@FunctionalInterface
public interface TopologyChangeListener extends Consumer<io.axoniq.axonserver.grpc.control.TopologyChange> {

    @Override
    default void accept(io.axoniq.axonserver.grpc.control.TopologyChange change) {
        onChange(new TopologyChange(change));
    }

    /**
     * A handler towards a {@link TopologyChange} from the {@link AxonServerConfiguration#getContext() context} this
     * listener was registered to.
     *
     * @param change The {@code TopologyChange} that transpired for the
     *               {@link AxonServerConfiguration#getContext() context} this listener was registered to.
     */
    void onChange(TopologyChange change);
}
