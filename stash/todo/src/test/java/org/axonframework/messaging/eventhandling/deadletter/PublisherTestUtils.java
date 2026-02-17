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

package org.axonframework.messaging.eventhandling.deadletter;

import jakarta.annotation.Nonnull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test utilities for collecting items from {@link Flow.Publisher publishers}.
 */
public final class PublisherTestUtils {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private PublisherTestUtils() {
    }

    public static <T> List<T> collect(@Nonnull Flow.Publisher<T> publisher) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        List<T> results = new ArrayList<>();

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {
                results.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });

        await(done);
        if (error.get() != null) {
            throw new AssertionError("Publisher failed", error.get());
        }
        return results;
    }

    public static <T> List<List<T>> collectNested(@Nonnull Flow.Publisher<Flow.Publisher<T>> publisher) {
        List<Flow.Publisher<T>> nested = collect(publisher);
        List<List<T>> result = new ArrayList<>();
        nested.forEach(p -> result.add(collect(p)));
        return result;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Publisher did not complete within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for publisher", e);
        }
    }
}

