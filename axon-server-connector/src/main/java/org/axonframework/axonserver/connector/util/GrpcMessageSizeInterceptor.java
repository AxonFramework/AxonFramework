/*
 * Copyright (c) 2010-2025. Axon Framework
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

import com.google.protobuf.MessageLite;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interceptor around a gRPC request that checks whether the message size is within the limits of the server.
 * This is done for both outbound and inbound messages.
 * <p>
 * If an outbound message exceeds the maximum message size, a {@link GrpcMessageSizeExceededException} is thrown.
 * Then, if the message's size exceeds the warning threshold, a warning is logged with a stack trace to debug the issue.
 * <p>
 * Inbound messages are only logged as warnings if they exceed the warning threshold.
 * <p>
 * When during initialization, the message size is greater than the default configuration of Axon Server,
 * we inform the user that this can cause issues if they don't increase the maximum message size on the server.
 * <p>
 * The performance overhead of this interceptor is minimal, as it uses a simple check that GRPC does while sending the
 * message anyway.
 *
 * @author Mitchell Herrijgers
 * @since 4.11.0
 */
public class GrpcMessageSizeInterceptor implements ClientInterceptor {
    private final Logger logger = LoggerFactory.getLogger(GrpcMessageSizeInterceptor.class);

    private final int maxMessageSizeWarningPercentage;
    private final int maxMessageSizeInBytes;
    private final int maxMessageSizeWarningInBytes;

    public GrpcMessageSizeInterceptor(
            int maxMessageSizeInBytes,
            double maxMessageSizeWarningPercentage
    ) {
        this.maxMessageSizeInBytes = maxMessageSizeInBytes;
        this.maxMessageSizeWarningPercentage = (int) (maxMessageSizeWarningPercentage * 100);
        this.maxMessageSizeWarningInBytes = (int) Math.ceil(maxMessageSizeInBytes * maxMessageSizeWarningPercentage);

        if (maxMessageSizeInBytes > 4194304) {
            logger.info("Your outgoing Axon Server messages will be limited to {} bytes, which is greater than the default maximum of 4194304 bytes.\n" +
                            "Make sure the `axoniq.axonserver.max-message-size` property is set to at least {} to prevent issues such as connection instability.",
                    maxMessageSizeInBytes, maxMessageSizeInBytes);
        }
    }


    @Override
    public <REQ, RESP> ClientCall<REQ, RESP> interceptCall(MethodDescriptor<REQ, RESP> methodDescriptor,
                                                           CallOptions callOptions,
                                                           Channel channel) {
        ClientCall<REQ, RESP> call = channel.newCall(methodDescriptor, callOptions);

        return new ForwardingClientCall.SimpleForwardingClientCall<REQ, RESP>(call) {
            @Override
            public void sendMessage(REQ message) {

                if (message instanceof MessageLite) {
                    int messageLength = ((MessageLite) message).getSerializedSize();
                    if (logger.isDebugEnabled()) {
                        logger.debug("Sending outbound gRPC message {} with size {}", messageLength, message.getClass().getName());
                    }
                    if (messageLength > maxMessageSizeInBytes) {
                        // It exceeds the (supposed) max message size of the server. Throw an exception to prevent disconnection.
                        throw new GrpcMessageSizeExceededException(String.format("Message size for %s exceeds the maximum allowed size. \n"
                                        + "Actual size: %d, Maximum size: %d",
                                message.getClass().getName(),
                                messageLength,
                                maxMessageSizeWarningInBytes));
                    }

                    if (messageLength > maxMessageSizeWarningInBytes) {
                        logger.warn("Outbound gRPC message of type {} exceeds {}% of the maximum allowed size. \n"
                                        + "Increase the max-message-size on both your Axon Framework application and Axon Server to allow for larger messages. \n"
                                        + "Actual size: {}, Maximum size: {}",
                                message.getClass().getName(),
                                maxMessageSizeWarningPercentage,
                                messageLength,
                                maxMessageSizeInBytes,
                                new GrpcMessageSizeWarningThresholdReachedException());
                    }
                }
                super.sendMessage(message);
            }

            @Override
            public void start(Listener<RESP> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RESP>(
                        responseListener
                ) {
                    @Override
                    public void onMessage(RESP message) {
                        if (message instanceof MessageLite) {
                            int messageLength = ((MessageLite) message).getSerializedSize();
                            if (logger.isDebugEnabled()) {
                                logger.debug("Received inbound gRPC message {} with size {}", messageLength, message.getClass().getName());
                            }

                            if (messageLength > maxMessageSizeWarningInBytes) {
                                logger.warn("Inbound gRPC message of type {} exceeds {}% of the maximum allowed size. \n"
                                                + "Increase the max-message-size on both your Axon Framework application and Axon Server to allow for larger messages. \n"
                                                + "Actual size: {}, Maximum size: {}",
                                        message.getClass().getName(),
                                        maxMessageSizeWarningPercentage,
                                        messageLength,
                                        maxMessageSizeInBytes,
                                        new GrpcMessageSizeWarningThresholdReachedException());
                            }
                        }
                        super.onMessage(message);
                    }
                }, headers);
            }
        };
    }
}
