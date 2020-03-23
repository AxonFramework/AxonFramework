package org.axonframework.axonserver.connector;

import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.InstructionResult;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import org.axonframework.axonserver.connector.util.ExceptionSerializer;

import java.util.function.Supplier;

import static io.axoniq.axonserver.grpc.ErrorMessage.newBuilder;
import static org.axonframework.axonserver.connector.ErrorCode.INSTRUCTION_EXECUTION_ERROR;

/**
 * Responsible for publishing the {@link InstructionResult} messages through a gRPC call to Axon Server.
 *
 * @author Sara Pellegrini
 * @since 4.4
 */
public class GrpcInstructionResultPublisher implements InstructionResultPublisher {

    private final Supplier<String> clientId;

    private final Publisher<PlatformInboundInstruction> gRPCPublisher;

    /**
     * Constructs a {@link GrpcInstructionResultPublisher} based on the specified {@link AxonServerConnectionManager}
     * and {@link AxonServerConfiguration}.
     *
     * @param connectionManager used to publish a message to Axon Server through a platform instruction gRPC call
     * @param configuration     used to get the client identifier of the application
     */
    public GrpcInstructionResultPublisher(AxonServerConnectionManager connectionManager,
                                          AxonServerConfiguration configuration) {
        this(configuration::getClientId,
             instruction -> connectionManager.send(configuration.getContext(), instruction));
    }

    /**
     * Constructs a {@link GrpcInstructionResultPublisher} based on the specified client id supplier and gRPC
     * message publisher.
     *
     * @param clientId      used to construct the {@link InstructionResult} message to be sent to Axon Server
     * @param gRPCPublisher the publisher responsible to send the {@link InstructionResult} message to Axon Server
     */
    public GrpcInstructionResultPublisher(Supplier<String> clientId,
                                          Publisher<PlatformInboundInstruction> gRPCPublisher) {
        this.clientId = clientId;
        this.gRPCPublisher = gRPCPublisher;
    }

    @Override
    public void publishSuccessFor(String instructionId) {
        PlatformInboundInstruction message = PlatformInboundInstruction
                .newBuilder()
                .setResult(InstructionResult
                                   .newBuilder()
                                   .setSuccess(true)
                                   .setInstructionId(instructionId))
                .build();
        gRPCPublisher.publish(message);
    }


    @Override
    public void publishFailureFor(String instructionId, Throwable error) {
        ErrorMessage errorMessage = newBuilder(ExceptionSerializer.serialize(clientId.get(), error))
                .setErrorCode(INSTRUCTION_EXECUTION_ERROR.errorCode())
                .build();
        publishFailure(instructionId, errorMessage);
    }

    @Override
    public void publishFailureFor(String instructionId, String errorDescription) {
        ErrorMessage errorMessage = newBuilder()
                .setMessage(errorDescription)
                .setErrorCode(INSTRUCTION_EXECUTION_ERROR.errorCode())
                .build();
        publishFailure(instructionId, errorMessage);
    }

    private void publishFailure(String instructionId, ErrorMessage errorMessage) {
        PlatformInboundInstruction message = PlatformInboundInstruction
                .newBuilder()
                .setResult(InstructionResult
                                   .newBuilder()
                                   .setSuccess(false)
                                   .setInstructionId(instructionId)
                                   .setError(errorMessage))
                .build();
        gRPCPublisher.publish(message);
    }
}
