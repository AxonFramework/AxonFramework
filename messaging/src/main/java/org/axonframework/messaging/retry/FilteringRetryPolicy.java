/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.messaging.retry;

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Message;

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

/**
 * A RetryPolicy that delegates to another RetryPolicy when the latest exception matches a given predicate.
 */
public class FilteringRetryPolicy implements RetryPolicy {

    private final RetryPolicy delegate;
    private final Predicate<Throwable> retryableErrorPredicate;

    /**
     * Initializes a RetryPolicy to wrap given {@code delegate} RetryPolicy when the latest failure matches the given
     * {@code retryableErrorPredicate}
     *
     * @param delegate                The policy to delegate to
     * @param retryableErrorPredicate The predicate matching errors that can be retried
     */
    public FilteringRetryPolicy(RetryPolicy delegate, Predicate<Throwable> retryableErrorPredicate) {
        this.delegate = delegate;
        this.retryableErrorPredicate = retryableErrorPredicate;
    }

    @Override
    public Outcome defineFor(@Nonnull Message<?> message, @Nonnull Throwable cause,
                             @Nonnull List<Class<? extends Throwable>[]> previousFailures) {
        if (retryableErrorPredicate.test(cause)) {
            return delegate.defineFor(message, cause, previousFailures);
        }
        return Outcome.doNotReschedule();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("filter", retryableErrorPredicate.toString());
    }
}
