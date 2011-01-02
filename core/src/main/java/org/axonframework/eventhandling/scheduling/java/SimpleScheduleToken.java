/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.scheduling.java;

import org.axonframework.eventhandling.scheduling.ScheduleToken;

import java.util.concurrent.ScheduledFuture;

/**
 * ScheduleToken for tasks event scheduled using the SimpleEventScheduler.
 *
 * @author Allard Buijze
 * @since 0.7
 */
class SimpleScheduleToken implements ScheduleToken {

    private static final long serialVersionUID = 1894122648511994000L;
    private final ScheduledFuture<?> future;

    /**
     * Creates a SimpleScheduleToken for the given <code>future</code>.
     *
     * @param future The future referencing the scheduled task.
     */
    public SimpleScheduleToken(ScheduledFuture<?> future) {
        this.future = future;
    }

    /**
     * Returns the future referencing the scheduled task.
     *
     * @return the future referencing the scheduled task
     */
    public ScheduledFuture<?> getFuture() {
        return future;
    }
}
