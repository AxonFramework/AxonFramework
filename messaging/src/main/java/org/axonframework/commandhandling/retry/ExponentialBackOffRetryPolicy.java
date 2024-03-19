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

package org.axonframework.commandhandling.retry;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.infra.ComponentDescriptor;

import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * A RetryScheduler that uses a backoff strategy, retrying commands at increasing intervals when they fail because of an
 * exception that is not explicitly non-transient. Checked exceptions are considered non-transient and will not result
 * in a retry.
 *
 * @author Bert Laverman
 * @since 4.2
 */
public class ExponentialBackOffRetryPolicy implements RetryPolicy {

    private final long initialWaitTime;

    public ExponentialBackOffRetryPolicy(long initialWaitTime) {
        this.initialWaitTime = initialWaitTime;
    }

    protected long computeRetryInterval(int failureCount) {
        final int retryCount = failureCount;
        return initialWaitTime * (1L << (retryCount - 1));
    }

    @Override
    public Outcome defineFor(CommandMessage<?> commandMessage, Throwable cause,
                             List<Class<? extends Throwable>[]> previousFailures) {
        return Outcome.rescheduleIn(computeRetryInterval(previousFailures.size()), TimeUnit.MILLISECONDS);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("initialWaitTime", initialWaitTime);
    }
}
