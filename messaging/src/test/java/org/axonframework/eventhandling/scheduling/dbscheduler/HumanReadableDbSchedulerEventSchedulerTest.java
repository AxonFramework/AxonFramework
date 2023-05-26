/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventhandling.scheduling.dbscheduler;

import com.github.kagkarlsson.scheduler.task.Execution;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.axonframework.utils.DbSchedulerTestUtil.getAndStartScheduler;
import static org.junit.jupiter.api.Assertions.*;

class HumanReadableDbSchedulerEventSchedulerTest extends AbstractDbSchedulerEventSchedulerTest {


    @Override
    Task<?> getTask() {
        return DbSchedulerEventScheduler.humanReadableTask();
    }

    @Override
    boolean useBinaryPojo() {
        return false;
    }

    @Test
    void whenNotInitializedThrow() {
        eventScheduler.shutdown();
        scheduler = getAndStartScheduler(dataSource, getTask());
        try {
            TaskInstance<DbSchedulerHumanReadableEventData> instance =
                    DbSchedulerEventScheduler.humanReadableTask()
                                             .instance("id", new DbSchedulerHumanReadableEventData());
            scheduler.schedule(instance, Instant.now());
            await().atMost(Duration.ofSeconds(1L)).untilAsserted(
                    () -> {
                        List<Execution> failures = scheduler.getFailingExecutions(Duration.ofHours(1L));
                        assertEquals(1, failures.size());
                        assertNotNull(failures.get(0).lastFailure);
                    }
            );
        } finally {
            scheduler.stop();
        }
    }
}
