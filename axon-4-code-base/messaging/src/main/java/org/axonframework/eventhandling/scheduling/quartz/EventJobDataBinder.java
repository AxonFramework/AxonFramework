/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling.scheduling.quartz;

import org.quartz.JobDataMap;

/**
 * Strategy towards reading/writing an Axon EventMessage from/to a Quartz {@link JobDataMap}.
 * <p>
 * Implementors may choose how to store the event message as serializable data in {@link JobDataMap}.
 * This is useful when one does not want to be limited to event payloads requiring to be {@link java.io.Serializable}
 * (used by Quartz job data map serialization).
 * </p>
 *
 * @see QuartzEventScheduler
 * @see FireEventJob
 */
public interface EventJobDataBinder {

    /**
     * Write an {@code eventMessage} (or its payload) to a {@link JobDataMap}.
     *
     * @param eventMessage to write to the {@link JobDataMap}
     * @return {@link JobDataMap} written to (must not be null)
     */
    JobDataMap toJobData(Object eventMessage);

    /**
     * Read an {@link org.axonframework.eventhandling.EventMessage} (or its payload) from the {@link JobDataMap}.
     *
     * @param jobData to read from
     * @return event message (or its payload)
     */
    Object fromJobData(JobDataMap jobData);

}
