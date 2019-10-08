package org.axonframework.axonserver.connector.heartbeat.grpc;

import io.axoniq.axonserver.grpc.control.Heartbeat;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.heartbeat.HeartbeatSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * gRPC implementation of {@link HeartbeatSource}, which send to AxonServer a {@link Heartbeat} message.
 *
 * @author Sara Pellegrini
 * @since 4.2
 */
public class GrpcHeartbeatSource implements HeartbeatSource {

    private final Logger log = LoggerFactory.getLogger(GrpcHeartbeatSource.class);

    private final Consumer<PlatformInboundInstruction> platformInstructionSender;

    public GrpcHeartbeatSource(AxonServerConnectionManager connectionManager, String context) {
        this.platformInstructionSender = instruction -> connectionManager.send(context, instruction);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Send to AxonServer a {@link PlatformInboundInstruction} that contains an {@link Heartbeat}.
     */
    @Override
    public void send() {
        try {
            PlatformInboundInstruction instruction = PlatformInboundInstruction
                    .newBuilder()
                    .setHeartbeat(Heartbeat.newBuilder())
                    .build();
            platformInstructionSender.accept(instruction);
        } catch (Exception e) {
            log.warn("Problem sending heartbeat to AxonServer.", e);
        }
    }
}
