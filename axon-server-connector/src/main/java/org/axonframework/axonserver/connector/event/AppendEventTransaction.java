/*
 * Copyright (c) 2018. AxonIQ
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.event;

import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.Confirmation;
import org.axonframework.axonserver.connector.event.util.EventCipher;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends one or more events to AxonServer in a single transaction.
 */
public class AppendEventTransaction {
    private final StreamObserver<Event> eventStreamObserver;
    private final CompletableFuture<Confirmation> observer;
    private final EventCipher eventCipher;

    public AppendEventTransaction(StreamObserver<Event> eventStreamObserver, CompletableFuture<Confirmation> observer, EventCipher eventCipher) {
        this.eventStreamObserver = eventStreamObserver;
        this.observer = observer;
        this.eventCipher = eventCipher;
    }

    public void append(Event event) {
        eventStreamObserver.onNext(eventCipher.encrypt(event));
    }

    public void commit() throws InterruptedException, ExecutionException, TimeoutException {
        eventStreamObserver.onCompleted();
        observer.get(10, TimeUnit.SECONDS);
    }

    public void rollback(Throwable reason) {
        eventStreamObserver.onError(reason);
    }

}
