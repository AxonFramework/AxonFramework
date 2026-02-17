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

package org.axonframework.messaging.deadletter;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;

import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * A {@link Flow.Publisher} that immediately terminates subscribers with a failure.
 *
 * @param <T> Type of element this publisher would emit.
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@Internal
public class FailedPublisher<T> implements Flow.Publisher<T> {

    private static final Flow.Subscription EMPTY_SUBSCRIPTION = new Flow.Subscription() {
        @Override
        public void request(long n) {
            // no-op
        }

        @Override
        public void cancel() {
            // no-op
        }
    };

    private final Throwable failure;

    /**
     * Constructs a publisher that fails all subscribers with the given {@code failure}.
     *
     * @param failure Failure to send to all subscribers.
     */
    public FailedPublisher(@Nonnull Throwable failure) {
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
        subscriber.onError(failure);
    }
}

