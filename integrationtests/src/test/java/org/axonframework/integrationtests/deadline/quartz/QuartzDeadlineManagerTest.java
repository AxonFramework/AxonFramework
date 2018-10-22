/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.integrationtests.deadline.quartz;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.deadline.quartz.QuartzDeadlineManager;
import org.axonframework.integrationtests.deadline.AbstractDeadlineManagerTestSuite;
import org.axonframework.deadline.DeadlineManager;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

public class QuartzDeadlineManagerTest extends AbstractDeadlineManagerTestSuite {

    @Override
    public DeadlineManager buildDeadlineManager(Configuration configuration) {
        try {
            Scheduler scheduler = new StdSchedulerFactory().getScheduler();
            QuartzDeadlineManager quartzDeadlineManager =
                    QuartzDeadlineManager.builder()
                                         .scheduler(scheduler)
                                         .scopeAwareProvider(new ConfigurationScopeAwareProvider(configuration))
                                         .build();
            scheduler.start();
            return quartzDeadlineManager;
        } catch (SchedulerException e) {
            throw new AxonConfigurationException("Unable to configure quartz scheduler", e);
        }
    }
}
