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
