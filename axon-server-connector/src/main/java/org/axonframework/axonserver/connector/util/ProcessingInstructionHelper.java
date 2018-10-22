package org.axonframework.axonserver.connector.util;

import io.axoniq.axonserver.grpc.ProcessingInstruction;
import io.axoniq.axonserver.grpc.ProcessingKey;

import java.util.List;

/**
 * Author: marc
 */
public class ProcessingInstructionHelper {
    private static long getProcessingInstructionNumber(List<ProcessingInstruction> processingInstructions, ProcessingKey key) {
        return processingInstructions.stream()
                                     .filter(pi -> key.equals(pi.getKey()))
                                     .map(pi -> pi.getValue().getNumberValue())
                                     .findFirst()
                                     .orElse(0L);
    }

    public static long priority(List<ProcessingInstruction> processingInstructions) {
        return getProcessingInstructionNumber(processingInstructions, ProcessingKey.PRIORITY);
    }

    public static long numberOfResults(List<ProcessingInstruction> processingInstructions) {
        return getProcessingInstructionNumber(processingInstructions, ProcessingKey.NR_OF_RESULTS);
    }

}
