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
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * A RetryScheduler that uses a backoff strategy, doubling the retry delay after each attempt.
 *
 * @author Bert Laverman
 * @author Allard Buijze
 * @since 4.2
 */
public class ExponentialBackOffRetryPolicy implements RetryPolicy {

    private final long initialWaitTime;

    /**
     * Initializes an exponential delay policy with given {@code initialWaitTime} in milliseconds.
     *
     * @param initialWaitTime the wait time for the first retry
     */
    public ExponentialBackOffRetryPolicy(long initialWaitTime) {
        this.initialWaitTime = initialWaitTime;
    }

    @Override
    public Outcome defineFor(@Nonnull Message<?> message, @Nonnull Throwable failure,
                             @Nonnull List<Class<? extends Throwable>[]> previousFailures) {
        if (Long.numberOfLeadingZeros(initialWaitTime) <= previousFailures.size()) {
            return Outcome.rescheduleIn(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        long waitTime = initialWaitTime << previousFailures.size();
        return Outcome.rescheduleIn(waitTime, TimeUnit.MILLISECONDS);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("initialWaitTime", initialWaitTime);
    }
}
