package org.axonframework.axonserver.connector.heartbeat.grpc;

import io.axoniq.axonserver.grpc.control.Heartbeat;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.axonserver.connector.heartbeat.HeartbeatSource;

import java.util.function.Consumer;

/**
 * @author Sara Pellegrini
 * @since 4.2
 */
public class GrpcHeartbeatSource implements HeartbeatSource {

    private final Consumer<PlatformInboundInstruction> platformInstructionSender;

    public GrpcHeartbeatSource(AxonServerConnectionManager connectionManager, String context) {
        this(instruction -> connectionManager.send(context, instruction));
    }

    public GrpcHeartbeatSource(
            Consumer<PlatformInboundInstruction> platformInstructionSender) {
        this.platformInstructionSender = platformInstructionSender;
    }

    @Override
    public void send() {
        PlatformInboundInstruction instruction = PlatformInboundInstruction
                .newBuilder()
                .setHeartbeat(Heartbeat.newBuilder())
                .build();
        platformInstructionSender.accept(instruction);
    }
}
