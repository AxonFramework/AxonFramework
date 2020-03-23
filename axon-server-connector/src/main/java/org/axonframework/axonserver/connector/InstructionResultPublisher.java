package org.axonframework.axonserver.connector;

/**
 * Interface that encapsulates the instruction result publication functionality.
 *
 * @author Sara Pellegrini
 * @since 4.4
 */
public interface InstructionResultPublisher {

    /**
     * Notifies to Axon Server a successful execution of the specified instruction.
     *
     * @param instructionId the identifier of the instruction that has been successfully processed.
     */
    void publishSuccessFor(String instructionId);

    /**
     * Notifies to Axon Server a failure during the execution of the specified instruction.
     *
     * @param instructionId the identifier of the instruction.
     * @param error         the error happened during the instruction execution.
     */
    void publishFailureFor(String instructionId, Throwable error);

    /**
     * Notifies to Axon Server a failure during the execution of the specified instruction.
     *
     * @param instructionId    the identifier of the instruction.
     * @param errorDescription the description of the error happened during the instruction execution.
     */
    void publishFailureFor(String instructionId, String errorDescription);
}
