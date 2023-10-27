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
 * EventJobDataBinder implementation that adds support for Event publication Jobs scheduled in Axon 2.
 */
public class LegacyAwareJobDataBinder implements EventJobDataBinder {
    /**
     * The key under which the events were scheduled in Axon 2. To allow jobs schedules in Axon 2 to be triggered
     * in Axon 3, these keys also need to be checked.
     */
    private static final String LEGACY_EVENT_KEY = "org.axonframework.domain.EventMessage";

    private final EventJobDataBinder delegate;

    /**
     * Initialize the Job Data Binder to add legacy support while still allowing the default Axon 3 Job Data format
     */
    public LegacyAwareJobDataBinder() {
        this(new QuartzEventScheduler.DirectEventJobDataBinder());
    }

    /**
     * Initialize the LegacyAwareJobDataBinder to add legacy job support, reverting to the given {@code delegate} when
     * the given job is not a legacy job definition.
     *
     * @param delegate The job data binder to bind non legacy jobs with.
     */
    public LegacyAwareJobDataBinder(EventJobDataBinder delegate) {
        this.delegate = delegate;
    }

    @Override
    public JobDataMap toJobData(Object eventMessage) {
        return delegate.toJobData(eventMessage);
    }

    @Override
    public Object fromJobData(JobDataMap jobData) {
        Object message = delegate.fromJobData(jobData);
        if (message == null) {
            message = jobData.get(LEGACY_EVENT_KEY);
        }
        return message;
    }
}
