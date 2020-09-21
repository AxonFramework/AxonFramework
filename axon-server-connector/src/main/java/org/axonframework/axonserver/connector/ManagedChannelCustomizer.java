package org.axonframework.axonserver.connector;

import io.grpc.ManagedChannelBuilder;
import java.util.function.UnaryOperator;

/**
 * Customizer to add more customizations to a managed channel to Axon Server.
 *
 * @author Marc Gathier
 * @since 4.4.3
 */
public interface ManagedChannelCustomizer extends UnaryOperator<ManagedChannelBuilder<?>> {

    /**
     * Returns a no-op {@link ManagedChannelCustomizer}.
     * @return a no-op {@link ManagedChannelCustomizer}
     */
    static ManagedChannelCustomizer identity() {
        return c -> c;
    }
}
