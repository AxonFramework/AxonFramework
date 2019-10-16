package org.axonframework.axonserver.connector.heartbeat.source;

import io.axoniq.axonserver.grpc.control.Heartbeat;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.heartbeat.HeartbeatSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * gRPC implementation of {@link HeartbeatSource}, which sends to AxonServer a {@link Heartbeat} message.
 *
 * @author Sara Pellegrini
 * @since 4.2.1
 */
public class GrpcHeartbeatSource implements HeartbeatSource {

    private static final Logger logger = LoggerFactory.getLogger(GrpcHeartbeatSource.class);

    private final Consumer<PlatformInboundInstruction> platformInstructionSender;

    /**
     * Creates a {@link GrpcHeartbeatSource} which uses an {@link AxonServerConnectionManager} to send heartbeats
     * messages to AxonServer.
     *
     * @param connectionManager the {@link AxonServerConnectionManager}
     * @param context           defines the (Bounded) Context for which heartbeats are sent
     */
    public GrpcHeartbeatSource(AxonServerConnectionManager connectionManager, String context) {
        this.platformInstructionSender = instruction -> connectionManager.send(context, instruction);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sends a {@link PlatformInboundInstruction} to AxonServer that contains a {@link Heartbeat} message.
     */
    @Override
    public void pulse() {
        try {
            PlatformInboundInstruction instruction = PlatformInboundInstruction
                    .newBuilder()
                    .setHeartbeat(Heartbeat.newBuilder())
                    .build();
            platformInstructionSender.accept(instruction);
        } catch (Exception e) {
            logger.warn("Problem sending heartbeat to AxonServer.", e);
        }
    }
}
