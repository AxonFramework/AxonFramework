/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.timeout;

import org.axonframework.common.AxonThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Unique instance of the ScheduledExecutorService used for scheduling interrupting tasks
 * for the {@link AxonTimeLimitedTask}, and the default logger for janitor-related threads.
 *
 * @author Mitchell Herrijgers
 * @see AxonTimeLimitedTask
 * @since 4.11.0
 */
public class AxonTaskJanitor {

    public static final ScheduledExecutorService INSTANCE = createJanitorExecutorService();
    public static final Logger LOGGER = LoggerFactory.getLogger("axon-janitor");

    private AxonTaskJanitor() {
        // Utility class
    }

    /**
     * Creates the ScheduledExecutorService used for scheduling the interrupting task. It only has one thread as the
     * load is very low. Cancelling the tasks will clean it up to reduce memory pressure.
     *
     * @return The ScheduledExecutorService
     */
    private static ScheduledThreadPoolExecutor createJanitorExecutorService() {
        ScheduledThreadPoolExecutor janitor = new ScheduledThreadPoolExecutor(1,
                                                                                  new AxonThreadFactory("axon-janitor"));
        // Clean up tasks in the queue when canceled. Performance is equal but reduces memory pressure.
        janitor.setRemoveOnCancelPolicy(true);
        return janitor;
    }
}
