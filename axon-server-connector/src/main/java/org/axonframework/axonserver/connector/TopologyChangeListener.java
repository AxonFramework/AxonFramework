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
 *     <li>{@link TopologyChange#getClientId() The client id} - The {@link AxonServerConfiguration#getClientId() identifier of the client} for which a topology change occurred.</li>
 *     <li>{@link TopologyChange#getClientStreamId() The client stream id} - The identifier of the stream/connection for a client (e.g., the Axon Framework node) for which a topology change occurred, defined by Axon Server.</li>
 *     <li>{@link TopologyChange#getComponentName() The component name} - The {@link AxonServerConfiguration#getComponentName() name of the component} for which a topology change occurred.</li>
 *     <li>{@link TopologyChange#getCommand() The command subscription identifier} <b>or</b> {@link TopologyChange#getQuery() The query subscription identifier} - The identifier of the handler that was added or removed</li>
 * </ul>
 * Here is an example {@code TopologyChange} for a command subscription:
 * <pre>{@code
 * context: "axoniq-university"
 * client_id: "149466@axoniq-uni"
 * client_stream_id: "149466@axoniq-uni.2bb5c0b4-59d9-4396-ad14-a32059f71d9a"
 * component_name: "AxonIQ University"
 * command {
 *   name: "io.axoniq.demo.university.faculty.write.createcourse.CreateCourse"
 *   load_factor: 100
 * }
 * }</pre>
 * <p>
 * An example use case of this functional interface, is to get notified when a new instance connects or disconnects. A
 * newly connecting instance would mean a new {@link TopologyChange#getClientStreamId()} entered, while a disconnect is
 * signaled by the {@code RESET_ALL} {@link TopologyChange#getUpdateType()}. Such a coarse topology change means the
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
