package org.axonframework.axonserver.connector.heartbeat;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction.RequestCase.HEARTBEAT;

/**
 * @author Sara Pellegrini
 * @since 4.2
 */
public class HeartbeatConnectionCheck implements ConnectionSanityCheck {

    private final ConnectionSanityCheck delegate;

    private final long heartbeatTimeout;

    private final AtomicReference<Instant> lastReceivedHeartbeat = new AtomicReference<>();

    public HeartbeatConnectionCheck(AxonServerConnectionManager connectionManager, String context) {
        this(r -> connectionManager.onOutboundInstruction(context, HEARTBEAT, i -> r.run()),
             new ActiveGrpcChannelCheck(connectionManager, context));
    }

    public HeartbeatConnectionCheck(Consumer<Runnable> registration,
                                    ConnectionSanityCheck delegate) {
        this(5_000, registration, delegate);
    }

    public HeartbeatConnectionCheck(long heartbeatTimeout,
                                    Consumer<Runnable> registerOnHeartbeat,
                                    ConnectionSanityCheck delegate) {
        this.delegate = delegate;
        this.heartbeatTimeout = heartbeatTimeout;
        registerOnHeartbeat.accept(this::onHeartbeat);
    }

    private void onHeartbeat() {
        lastReceivedHeartbeat.set(Instant.now());
    }

    @Override
    public boolean isValid() {
        if (!delegate.isValid()) {
            return false;
        }
        Instant timeout = Instant.now().minus(heartbeatTimeout, ChronoUnit.MILLIS);
        Instant instant = lastReceivedHeartbeat.get();
        return instant == null || !instant.isBefore(timeout);
    }
}
