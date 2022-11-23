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

package org.axonframework.integrationtests.deadline.jobrunr;

import org.axonframework.deadline.jobrunnr.JobRunrDeadlineManager;
import org.jobrunr.server.JobActivator;

public class SimpleActivator implements JobActivator {

    private JobRunrDeadlineManager manager;

    SimpleActivator(JobRunrDeadlineManager manager) {
        this.manager = manager;
    }

    @Override
    public <T> T activateJob(Class<T> type) {
        if (type.isAssignableFrom(JobRunrDeadlineManager.class)) {
            return (T) manager;
        }
        return null;
    }
}
