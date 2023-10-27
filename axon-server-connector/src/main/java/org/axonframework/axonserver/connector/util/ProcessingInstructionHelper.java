/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.util;

import io.axoniq.axonserver.grpc.ProcessingInstruction;
import io.axoniq.axonserver.grpc.ProcessingKey;

import java.util.List;
import java.util.Optional;

/**
 * Utility class contain helper methods to extract information from {@link ProcessingInstruction}s.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public abstract class ProcessingInstructionHelper {

    private ProcessingInstructionHelper() {
        // Utility class
    }

    /**
     * Retrieve the priority as a {@code long} from the given {@code processingInstructions}, by searching for the
     * {@link ProcessingInstruction} who's key equals the {@link ProcessingKey#PRIORITY}.
     *
     * @param processingInstructions a {@link List} of {@link ProcessingInstruction}s to retrieve the {@link
     *                               ProcessingKey#PRIORITY} from
     * @return a {@code long} specifying the priority of a given operation
     */
    public static long priority(List<ProcessingInstruction> processingInstructions) {
        return getProcessingInstructionNumber(processingInstructions, ProcessingKey.PRIORITY).orElse(0L);
    }

    /**
     * Retrieve the desired 'number of results' as a {@code long} from the given {@code processingInstructions}, by
     * searching for the {@link ProcessingInstruction} who's key equals the {@link ProcessingKey#NR_OF_RESULTS}.
     *
     * @param processingInstructions a {@link List} of {@link ProcessingInstruction}s to retrieve the {@link
     *                               ProcessingKey#NR_OF_RESULTS} from
     * @return a {@code long} specifying the desired 'number of results' for a given operation
     */
    public static long numberOfResults(List<ProcessingInstruction> processingInstructions) {
        return getProcessingInstructionNumber(processingInstructions, ProcessingKey.NR_OF_RESULTS).orElse(1L);
    }

    /**
     * Retrieve whether Axon Server supports streaming from the given {@code processingInstructions}, by
     * searching for the value of {@link ProcessingKey#SERVER_SUPPORTS_STREAMING}.
     *
     * @param processingInstructions a {@link List} of {@link ProcessingInstruction}s to retrieve the {@link
     *                               ProcessingKey#SERVER_SUPPORTS_STREAMING} from
     * @return {@code true} if Axon Server supports streaming, {@code false} otherwise
     */
    public static boolean axonServerSupportsQueryStreaming(List<ProcessingInstruction> processingInstructions) {
        return getProcessingInstructionBoolean(processingInstructions,
                                               ProcessingKey.SERVER_SUPPORTS_STREAMING).orElse(false);
    }

    /**
     * Retrieve whether Client (query issuer) supports streaming from the given {@code
     * processingInstructions}, by searching for the value of {@link ProcessingKey#CLIENT_SUPPORTS_STREAMING}.
     *
     * @param processingInstructions a {@link List} of {@link ProcessingInstruction}s to retrieve the {@link
     *                               ProcessingKey#CLIENT_SUPPORTS_STREAMING} from
     * @return {@code true} if Client supports streaming, {@code false} otherwise
     */
    public static boolean clientSupportsQueryStreaming(List<ProcessingInstruction> processingInstructions) {
        return getProcessingInstructionBoolean(processingInstructions,
                                               ProcessingKey.CLIENT_SUPPORTS_STREAMING).orElse(false);
    }

    /**
     * Retrieve the desired 'number of results' as a {@code long} from the given {@code processingInstructions}, by
     * searching for the {@link ProcessingInstruction} who's key equals the {@link ProcessingKey#NR_OF_RESULTS}.
     *
     * @param processingInstructions a {@link List} of {@link ProcessingInstruction}s to retrieve the {@link
     *                               ProcessingKey#NR_OF_RESULTS} from
     * @return a {@code long} specifying the desired 'number of results' for a given operation
     */
    public static long timeout(List<ProcessingInstruction> processingInstructions) {
        return getProcessingInstructionNumber(processingInstructions, ProcessingKey.TIMEOUT).orElse(0L);
    }

    private static Optional<Long> getProcessingInstructionNumber(List<ProcessingInstruction> processingInstructions,
                                                                 ProcessingKey processingKey) {
        return processingInstructions.stream()
                                     .filter(instruction -> processingKey.equals(instruction.getKey()))
                                     .map(instruction -> instruction.getValue().getNumberValue())
                                     .findFirst();
    }

    private static Optional<Boolean> getProcessingInstructionBoolean(List<ProcessingInstruction> processingInstructions,
                                                                     ProcessingKey processingKey) {
        return processingInstructions.stream()
                                     .filter(instruction -> processingKey.equals(instruction.getKey()))
                                     .map(instruction -> instruction.getValue().getBooleanValue())
                                     .findFirst();
    }
}
