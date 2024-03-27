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
import org.axonframework.common.infra.DescribableComponent;

import java.util.List;
import java.util.concurrent.TimeUnit;

public interface RetryPolicy extends DescribableComponent {

    RetryPolicy.Outcome defineFor(CommandMessage<?> commandMessage, Throwable cause, List<Class<? extends Throwable>[]> previousFailures);

    sealed interface Outcome permits Outcome.RetryOutcome, Outcome.NoNotRetryOutcome {

        boolean shouldReschedule();

        long rescheduleInterval();

        TimeUnit rescheduleIntervalTimeUnit();

        static Outcome rescheduleIn(long interval, TimeUnit timeUnit) {
            return new RetryOutcome(interval, timeUnit);
        }

        static Outcome doNotReschedule() {
            return new NoNotRetryOutcome();
        }

        final class RetryOutcome implements Outcome {

            private final long interval;
            private final TimeUnit timeUnit;

            public RetryOutcome(long interval, TimeUnit timeUnit) {
                this.interval = interval;
                this.timeUnit = timeUnit;
            }

            @Override
            public boolean shouldReschedule() {
                return true;
            }

            @Override
            public long rescheduleInterval() {
                return interval;
            }

            @Override
            public TimeUnit rescheduleIntervalTimeUnit() {
                return timeUnit;
            }
        }

        final class NoNotRetryOutcome implements Outcome {

            @Override
            public boolean shouldReschedule() {
                return false;
            }

            @Override
            public long rescheduleInterval() {
                return 0;
            }

            @Override
            public TimeUnit rescheduleIntervalTimeUnit() {
                return null;
            }
        }
    }

}
