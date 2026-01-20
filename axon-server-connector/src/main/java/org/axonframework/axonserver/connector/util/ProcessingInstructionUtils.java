/*
 * Copyright (c) 2010-2026. Axon Framework
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

import io.axoniq.axonserver.grpc.MetaDataValue;
import io.axoniq.axonserver.grpc.ProcessingInstruction;
import io.axoniq.axonserver.grpc.ProcessingKey;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Optional;

/**
 * Utility class contain helper methods to extract information from {@link ProcessingInstruction}s.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public final class ProcessingInstructionUtils {

    private ProcessingInstructionUtils() {
        // Utility class
    }

    /**
     * Creates a new {@link ProcessingInstruction.Builder} with the given {@code key} and {@code value}.
     *
     * @param key   The {@link ProcessingKey} to set on the {@link ProcessingInstruction.Builder}
     * @param value The {@link MetaDataValue.Builder} to set on the {@link ProcessingInstruction.Builder}.
     * @return A {@link ProcessingInstruction.Builder} initialized with the given {@code key} and {@code value}.
     */
    public static ProcessingInstruction.Builder createProcessingInstruction(@Nonnull ProcessingKey key,
                                                                            @Nonnull MetaDataValue.Builder value) {
        return ProcessingInstruction.newBuilder().setKey(key).setValue(value);
    }

    /**
     * Creates a new {@link ProcessingInstruction.Builder} with the given {@code key} and {@code value}.
     *
     * @param key   The {@link ProcessingKey} to set on the {@link ProcessingInstruction.Builder}.
     * @param value The {@link String} value to set in the {@link MetaDataValue.Builder}, which is then used to
     *              initialize the {@link ProcessingInstruction.Builder}.
     * @return A {@link ProcessingInstruction.Builder} initialized with the provided {@code key} and {@code value}.
     * @see ProcessingInstructionUtils#createProcessingInstruction(ProcessingKey, MetaDataValue.Builder)
     */
    public static ProcessingInstruction.Builder createProcessingInstruction(@Nonnull ProcessingKey key,
                                                                            @Nonnull String value) {
        return ProcessingInstruction.newBuilder()
                                    .setKey(key)
                                    .setValue(MetaDataValue.newBuilder().setTextValue(value));
    }

    /**
     * Creates a new {@link ProcessingInstruction.Builder} with the given {@code key} and {@code value}.
     *
     * @param key   The {@link ProcessingKey} to set on the {@link ProcessingInstruction.Builder}.
     * @param value A {@code boolean} value used to initialize the {@link MetaDataValue.Builder}, which is then set on
     *              the {@link ProcessingInstruction.Builder}.
     * @return A {@link ProcessingInstruction.Builder} initialized with the provided {@code key} and {@code value}.
     * @see ProcessingInstructionUtils#createProcessingInstruction(ProcessingKey, MetaDataValue.Builder)
     */
    public static ProcessingInstruction.Builder createProcessingInstruction(@Nonnull ProcessingKey key,
                                                                            boolean value) {
        return ProcessingInstruction.newBuilder()
                                    .setKey(key)
                                    .setValue(MetaDataValue.newBuilder().setBooleanValue(value));
    }

    /**
     * Creates a new {@link ProcessingInstruction.Builder} with the given {@code key} and {@code value}.
     *
     * @param key   The {@link ProcessingKey} to set on the {@link ProcessingInstruction.Builder}.
     * @param value The {@code long} value to set in the {@link MetaDataValue.Builder}, which is then used to initialize
     *              the {@link ProcessingInstruction.Builder}.
     * @return A {@link ProcessingInstruction.Builder} initialized with the provided {@code key} and {@code value}.
     * @see ProcessingInstructionUtils#createProcessingInstruction(ProcessingKey, MetaDataValue.Builder)
     */
    public static ProcessingInstruction.Builder createProcessingInstruction(@Nonnull ProcessingKey key,
                                                                            long value) {
        return ProcessingInstruction.newBuilder()
                                    .setKey(key)
                                    .setValue(MetaDataValue.newBuilder().setNumberValue(value));
    }

    /**
     * Retrieve the routing key as a {@link String} from the given {@code instructions}, by searching for the
     * {@link ProcessingInstruction} who's key equals the {@link ProcessingKey#ROUTING_KEY}.
     *
     * @param instructions The {@link List} of {@link ProcessingInstruction}s to retrieve the
     *                     {@link ProcessingKey#ROUTING_KEY} from.
     * @return A {@link String} specifying the routing key for a given operation, or {@code null} if not found.
     */
    public static String routingKey(@Nonnull List<ProcessingInstruction> instructions) {
        return getProcessingInstructionString(instructions, ProcessingKey.ROUTING_KEY).orElse(null);
    }

    private static Optional<String> getProcessingInstructionString(List<ProcessingInstruction> instructions,
                                                                   ProcessingKey processingKey) {
        return instructions.stream()
                           .filter(instruction -> processingKey.equals(instruction.getKey()))
                           .map(instruction -> instruction.getValue().getTextValue())
                           .findFirst();
    }

    /**
     * Retrieve the priority as a {@code int} from the given {@code instructions}, by searching for the
     * {@link ProcessingInstruction} who's key equals the {@link ProcessingKey#PRIORITY}.
     *
     * @param instructions A {@link List} of {@link ProcessingInstruction}s to retrieve the
     *                     {@link ProcessingKey#PRIORITY} from.
     * @return An {@code int} specifying the priority of a given operation.
     */
    public static int priority(@Nonnull List<ProcessingInstruction> instructions) {
        return getProcessingInstructionNumber(instructions, ProcessingKey.PRIORITY).orElse(0L).intValue();
    }

    /**
     * Retrieve the desired 'number of results' as a {@code long} from the given {@code instructions}, by searching for
     * the {@link ProcessingInstruction} who's key equals the {@link ProcessingKey#NR_OF_RESULTS}.
     *
     * @param instructions A {@link List} of {@link ProcessingInstruction}s to retrieve the
     *                     {@link ProcessingKey#NR_OF_RESULTS} from.
     * @return A {@code long} specifying the desired 'number of results' for a given operation.
     */
    public static long numberOfResults(@Nonnull List<ProcessingInstruction> instructions) {
        return getProcessingInstructionNumber(instructions, ProcessingKey.NR_OF_RESULTS).orElse(1L);
    }

    /**
     * Retrieve whether Axon Server supports streaming from the given {@code instructions}, by searching for the value
     * of {@link ProcessingKey#SERVER_SUPPORTS_STREAMING}.
     *
     * @param instructions A {@link List} of {@link ProcessingInstruction}s to retrieve the
     *                     {@link ProcessingKey#SERVER_SUPPORTS_STREAMING} from.
     * @return {@code true} if Axon Server supports streaming, {@code false} otherwise.
     */
    public static boolean axonServerSupportsQueryStreaming(@Nonnull List<ProcessingInstruction> instructions) {
        return getProcessingInstructionBoolean(instructions,
                                               ProcessingKey.SERVER_SUPPORTS_STREAMING).orElse(false);
    }

    /**
     * Retrieve whether Client (query issuer) supports streaming from the given {@code instructions}, by searching for
     * the value of {@link ProcessingKey#CLIENT_SUPPORTS_STREAMING}.
     *
     * @param instructions A {@link List} of {@link ProcessingInstruction}s to retrieve the
     *                     {@link ProcessingKey#CLIENT_SUPPORTS_STREAMING} from.
     * @return {@code true} if Client supports streaming, {@code false} otherwise.
     */
    public static boolean clientSupportsQueryStreaming(@Nonnull List<ProcessingInstruction> instructions) {
        return getProcessingInstructionBoolean(instructions, ProcessingKey.CLIENT_SUPPORTS_STREAMING).orElse(false);
    }

    private static Optional<Boolean> getProcessingInstructionBoolean(List<ProcessingInstruction> instructions,
                                                                     ProcessingKey processingKey) {
        return instructions.stream()
                           .filter(instruction -> processingKey.equals(instruction.getKey()))
                           .map(instruction -> instruction.getValue().getBooleanValue())
                           .findFirst();
    }

    /**
     * Retrieve the desired 'number of results' as a {@code long} from the given {@code instructions}, by searching for
     * the {@link ProcessingInstruction} who's key equals the {@link ProcessingKey#NR_OF_RESULTS}.
     *
     * @param instructions A {@link List} of {@link ProcessingInstruction}s to retrieve the
     *                     {@link ProcessingKey#NR_OF_RESULTS} from.
     * @return a {@code long} specifying the desired 'number of results' for a given operation
     */
    public static long timeout(@Nonnull List<ProcessingInstruction> instructions) {
        return getProcessingInstructionNumber(instructions, ProcessingKey.TIMEOUT).orElse(0L);
    }

    private static Optional<Long> getProcessingInstructionNumber(List<ProcessingInstruction> instructions,
                                                                 ProcessingKey processingKey) {
        return instructions.stream()
                           .filter(instruction -> processingKey.equals(instruction.getKey()))
                           .map(instruction -> instruction.getValue().getNumberValue())
                           .findFirst();
    }
}