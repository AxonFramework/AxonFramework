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

import java.util.concurrent.TimeUnit;

final class RetryOutcome implements RetryPolicy.Outcome {

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
