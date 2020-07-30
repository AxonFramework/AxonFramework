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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.netty.util.internal.OutOfDirectMemoryError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Wrapper around {@link StreamObserver} that re-subscribes on error received (if other side is still available).
 *
 * @param <V> the type of values passed through the stream
 * @author Milan Savic
 * @since 4.1.2
 * @deprecated in through use of the <a href="https://github.com/AxonIQ/axonserver-connector-java">AxonServer java
 * connector</a>
 */
@Deprecated
public class ResubscribableStreamObserver<V> implements StreamObserver<V> {

    private static final Logger logger = LoggerFactory.getLogger(ResubscribableStreamObserver.class);

    private final StreamObserver<V> delegate;
    private final Consumer<Throwable> resubscribe;

    /**
     * Creates the Re-subscribable Stream Observer.
     *
     * @param delegate    the StreamObserver to delegate calls
     * @param resubscribe the re-subscription consumer - should implement the actual re-subscription
     */
    public ResubscribableStreamObserver(StreamObserver<V> delegate, Consumer<Throwable> resubscribe) {
        this.delegate = delegate;
        this.resubscribe = resubscribe;
    }

    @Override
    public void onNext(V value) {
        try {
            delegate.onNext(value);
        } catch (Exception | OutOfDirectMemoryError e) {
            onError(e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        logger.warn("A problem occurred in the stream.", throwable);
        delegate.onError(throwable);
        if (throwable instanceof StatusRuntimeException
                && ((StatusRuntimeException) throwable).getStatus().getCode().equals(Status.UNAVAILABLE.getCode())) {
            return;
        }
        logger.info("Resubscribing.");
        resubscribe.accept(throwable);
    }

    @Override
    public void onCompleted() {
        delegate.onCompleted();
    }
}
