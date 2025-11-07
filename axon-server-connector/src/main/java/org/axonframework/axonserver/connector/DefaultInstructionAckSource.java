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

package org.axonframework.axonserver.connector;

import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.InstructionAck;
import io.grpc.stub.StreamObserver;

import java.util.function.Function;

/**
 * Default implementation of {@link InstructionAckSource}.
 *
 * @param <T> the type of message to be sent
 * @author Milan Savic
 * @since 4.2.1
 */
public class DefaultInstructionAckSource<T> implements InstructionAckSource<T> {

    private final Function<InstructionAck, T> messageCreator;

    /**
     * Instantiates {@link DefaultInstructionAckSource}.
     *
     * @param messageCreator creates a message based on {@link InstructionAck}
     */
    public DefaultInstructionAckSource(Function<InstructionAck, T> messageCreator) {
        this.messageCreator = messageCreator;
    }

    @Override
    public void sendAck(String instructionId, boolean success, ErrorMessage error, StreamObserver<T> stream) {
        // Do not send an ack if instructionId is not set.
        // This way, requesting side can control for which instructions ack is needed
        if (instructionId == null || instructionId.equals("")) {
            return;
        }
        InstructionAck.Builder builder = InstructionAck.newBuilder()
                                                       .setInstructionId(instructionId)
                                                       .setSuccess(success);
        if (error != null) {
            builder.setError(error);
        }

        stream.onNext(messageCreator.apply(builder.build()));
    }
}
