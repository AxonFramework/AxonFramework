package org.axonframework.axonserver.connector.heartbeat;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction.RequestCase.HEARTBEAT;

/**
 * Implementation of {@link ConnectionSanityCheck} which verifies that heartbeats are properly received.
 *
 * @author Sara Pellegrini
 * @since 4.2.1
 */
public class HeartbeatConnectionCheck implements ConnectionSanityCheck {

    private static final long DEFAULT_HEARTBEAT_TIMEOUT_MILLIS = 5_000;

    private final ConnectionSanityCheck delegate;

    private final long heartbeatTimeout;

    private final Clock clock;

    private final AtomicReference<Instant> lastReceivedHeartbeat = new AtomicReference<>();

    /**
     * Constructs an instance of {@link HeartbeatConnectionCheck} using the specified parameters.
     *
     * @param connectionManager the connectionManager to AxonServer instance
     * @param context           the context
     */
    public HeartbeatConnectionCheck(AxonServerConnectionManager connectionManager, String context) {
        this(r -> connectionManager.onOutboundInstruction(context, HEARTBEAT, i -> r.run()),
             new ActiveGrpcChannelCheck(connectionManager, context));
    }

    /**
     * Constructs an instance of {@link HeartbeatConnectionCheck} using a default timeout of 5 seconds and the system clock.
     * @param registration function which allows to register a callback for the reception of an heartbeat
     * @param delegate another implementation of {@link ConnectionSanityCheck} that performs others kind of verifications
     */
    public HeartbeatConnectionCheck(Consumer<Runnable> registration,
                                    ConnectionSanityCheck delegate) {
        this(DEFAULT_HEARTBEAT_TIMEOUT_MILLIS, registration, delegate, Clock.systemUTC());
    }

    /**
     * Primary constructor of {@link HeartbeatConnectionCheck}.
     *
     * @param heartbeatTimeout the time without any heartbeat after which the connection is considered no more valid;
     *                         it is expressed in milliseconds
     * @param registerOnHeartbeat function which allows to register a callback for the reception of an heartbeat
     * @param delegate another implementation of {@link ConnectionSanityCheck} that performs others kind of verifications
     * @param clock clock used to verify the timeout
     */
    public HeartbeatConnectionCheck(long heartbeatTimeout,
                                    Consumer<Runnable> registerOnHeartbeat,
                                    ConnectionSanityCheck delegate, Clock clock) {
        this.clock = clock;
        this.delegate = delegate;
        this.heartbeatTimeout = heartbeatTimeout;
        registerOnHeartbeat.accept(this::onHeartbeat);
    }

    private void onHeartbeat() {
        lastReceivedHeartbeat.set(Instant.now(clock));
    }

    /**
     * {@inheritDoc}
     *
     * Detects if the connection is still available according with the heartbeat timeout.
     * If no heartbeat at all is received since the startup of the connection, this implementation returns true
     * as we suppose that Axon Server version doesn't support the heartbeat feature.
     *
     * @return true if the connection is valid, false otherwise
     */
    @Override
    public boolean isValid() {
        if (!delegate.isValid()) {
            return false;
        }
        Instant timeout = Instant.now(clock).minus(heartbeatTimeout, ChronoUnit.MILLIS);
        Instant instant = lastReceivedHeartbeat.get();
        return instant == null || !instant.isBefore(timeout);
    }
}
