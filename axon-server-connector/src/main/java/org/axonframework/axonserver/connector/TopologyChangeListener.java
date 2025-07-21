package org.axonframework.axonserver.connector;

import io.axoniq.axonserver.grpc.control.TopologyChange;

import java.util.function.Consumer;

/**
 * A functional interface representing a listener that is notified when a {@link TopologyChange} occurs to a specific
 * {@link AxonServerConfiguration#getContext() context}.
 * <p>
 * A {@code TopologyChange} can reflect any of the following {@link TopologyChange#getUpdateType() updates}:
 * <ol>
 *     <li>{@code ADD_COMMAND_HANDLER} - A <b>command</b> handler was added by a specific instance to the context the listener is attached to.</li>
 *     <li>{@code REMOVE_COMMAND_HANDLER} - A <b>command</b> handler was removed from a specific instance for the context the listener is attached to.</li>
 *     <li>{@code ADD_QUERY_HANDLER} - A <b>query</b> handler was added by a specific instance to the context the listener is attached to.</li>
 *     <li>{@code REMOVE_QUERY_HANDLER} - A <b>query</b> handler was removed from a specific instance for the context the listener is attached to.</li>
 *     <li>{@code RESET_ALL} - This signals the connection broke down of a specific instance for the context the listener is attached to.</li>
 * </ol>
 * <p>
 * A {@code TopologyChange} includes more information to better comprehend the actual switch, being:
 * <ul>
 *     <li>{@link TopologyChange#getContext() The context} - The context from which the change originates.</li>
 *     <li>{@link TopologyChange#getClientId() The client id} - The identifier of the client (e.g., the Axon Framework node) for which a topology change occurred.</li>
 *     <li>{@link TopologyChange#getClientStreamId() The client stream id} - The identifier of the stream/connection for a client (e.g., the Axon Framework node) for which a topology change occurred.</li>
 *     <li>{@link TopologyChange#getComponentName() The component name} - The name of the component (e.g., the Axon Framework node) for which a topology change occurred.</li>
 *     <li>{@link TopologyChange#getCommand() The command subscription identifier} <b>or</b> {@link TopologyChange#getQuery() The query subscription identifier} - The identifier of the handler that was added or removed</li>
 * </ul>
 * <p>
 * An example use case of this functional interface, is to get notified when a new instance connects or disconnects. A
 * newly connecting instance would mean a new {@link TopologyChange#getClientId()} entered, while a disconnect is
 * signaled by the {@code RESET_ALL} {@link TopologyChange#getUpdateType()}. Such a coarse topology change means the
 * routing of commands will change, which impacts {@link org.axonframework.common.caching.Cache} hits for your
 * aggregates or sagas, for example.
 *
 * @author Steven van Beelen
 * @since 4.12.0
 */
@FunctionalInterface
public interface TopologyChangeListener extends Consumer<TopologyChange> {

    @Override
    default void accept(TopologyChange change) {
        onChange(change);
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
