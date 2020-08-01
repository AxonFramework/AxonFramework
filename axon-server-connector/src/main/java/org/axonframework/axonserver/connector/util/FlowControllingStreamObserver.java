/*
 * Copyright (c) 2010-2020. Axon Framework
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

import io.axoniq.axonserver.grpc.FlowControl;
import io.grpc.stub.StreamObserver;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConfiguration.FlowControlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Wrapper around the standard StreamObserver that guarantees that the onNext calls are executed in a thread-safe
 * manner. Also maintains flow control sending a new message with permits to AxonServer when it is ready to handle more
 * messages
 *
 * @author Marc Gathier
 * @since 4.0
 * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
 * connector</a>
 */
@Deprecated
public class FlowControllingStreamObserver<T> implements StreamObserver<T> {

    private final StreamObserver<T> wrappedStreamObserver;

    private static final Logger logger = LoggerFactory.getLogger(FlowControllingStreamObserver.class);
    private final AtomicLong remainingPermits;
    private final long newPermits;
    private final long initialNrofPermits;
    private final String clientId;
    private final T newPermitsRequest;
    private final Predicate<T> isConfirmationMessage;
    private final Function<FlowControl, T> requestWrapper;

    /**
     * Constructs a {@link FlowControllingStreamObserver}.
     *
     * @param wrappedStreamObserver stream observer to send messages to AxonServer
     * @param configuration         AxonServer configuration for flow control
     * @param requestWrapper        Function to create a new permits request
     * @param isConfirmationMessage predicate to test if the message sent to AxonServer is a confirmation message
     */
    public FlowControllingStreamObserver(StreamObserver<T> wrappedStreamObserver,
                                         AxonServerConfiguration configuration,
                                         Function<FlowControl, T> requestWrapper,
                                         Predicate<T> isConfirmationMessage) {
        this(wrappedStreamObserver,
             configuration.getClientId(),
             configuration.getDefaultFlowControlConfiguration(),
             requestWrapper,
             isConfirmationMessage);
    }

    /**
     * Constructs a {@link FlowControllingStreamObserver}.
     *
     * @param wrappedStreamObserver    stream observer to send messages to AxonServer
     * @param clientId                 ClientId in AxonServer configuration
     * @param flowControlConfiguration Flow control configuration
     * @param requestWrapper           Function to create a new permits request
     * @param isConfirmationMessage    predicate to test if the message sent to AxonServer is a confirmation message
     */
    public FlowControllingStreamObserver(StreamObserver<T> wrappedStreamObserver, String clientId,
                                         FlowControlConfiguration flowControlConfiguration,
                                         Function<FlowControl, T> requestWrapper, Predicate<T> isConfirmationMessage) {
        this.wrappedStreamObserver = wrappedStreamObserver;
        this.clientId = clientId;
        this.remainingPermits = new AtomicLong(
                flowControlConfiguration.getInitialNrOfPermits().longValue() - flowControlConfiguration
                        .getNewPermitsThreshold().longValue()
        );
        this.newPermits = flowControlConfiguration.getNrOfNewPermits();
        this.initialNrofPermits = flowControlConfiguration.getInitialNrOfPermits();
        this.newPermitsRequest = requestWrapper.apply(createRequest(newPermits));
        this.isConfirmationMessage = isConfirmationMessage;
        this.requestWrapper = requestWrapper;
    }

    public FlowControllingStreamObserver<T> sendInitialPermits() {
        wrappedStreamObserver.onNext(requestWrapper.apply(createRequest(initialNrofPermits)));
        return this;
    }

    private FlowControl createRequest(long initialNrOfPermits) {
        return FlowControl.newBuilder()
                          .setClientId(clientId)
                          .setPermits(initialNrOfPermits)
                          .build();
    }

    @Override
    public void onNext(T t) {
        synchronized (wrappedStreamObserver) {
            wrappedStreamObserver.onNext(t);
        }
        logger.debug("Sending response to AxonServer platform, remaining permits: {}", remainingPermits.get());

        if (isConfirmationMessage.test(t)) {
            markConsumed(1);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        wrappedStreamObserver.onError(throwable);
    }

    @Override
    public void onCompleted() {
        logger.debug("Observer stopped");
        try {
            wrappedStreamObserver.onCompleted();
        } catch (Exception ignore) {

        }
    }

    /**
     * Notifies the stream observer that [@code consumed} messages are processed by the client. Triggers a new permits
     * request when remaining permits is 0.
     *
     * @param consumed nr of messages consumed
     */
    public void markConsumed(Integer consumed) {
        if (remainingPermits.updateAndGet(old -> old - consumed) == 0) {
            remainingPermits.addAndGet(newPermits);
            synchronized (wrappedStreamObserver) {
                wrappedStreamObserver.onNext(newPermitsRequest);
            }
            logger.info("Granting new permits: {}", newPermitsRequest);
        }
    }
}
