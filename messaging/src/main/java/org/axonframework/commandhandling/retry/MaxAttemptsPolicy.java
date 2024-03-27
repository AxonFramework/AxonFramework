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
import javax.annotation.Nonnull;

public class MaxAttemptsPolicy implements RetryPolicy {

    private final RetryPolicy delegate;
    private final int maxAttempts;

    public MaxAttemptsPolicy(RetryPolicy delegate, int retries) {
        this.delegate = delegate;
        this.maxAttempts = retries;
    }

    @Override
    public Outcome defineFor(CommandMessage<?> commandMessage, Throwable cause,
                             List<Class<? extends Throwable>[]> previousFailures) {
        if (previousFailures.size() < maxAttempts) {
            return delegate.defineFor(commandMessage, cause, previousFailures);
        } else {
            return Outcome.doNotReschedule();
        }
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("maxAttempts", maxAttempts);
    }
}
