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

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.jobrunr.JobRunrDeadlineManager;
import org.axonframework.integrationtests.deadline.AbstractDeadlineManagerTestSuite;
import org.axonframework.messaging.ScopeAwareProvider;
import org.jobrunr.configuration.JobRunr;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.junit.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.util.Objects;

import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobrunrDeadlineManagerTest extends AbstractDeadlineManagerTestSuite {

    private BackgroundJobServer backgroundJobServer;

    @AfterEach
    void cleanUp() {
        if (!Objects.isNull(backgroundJobServer)) {
            backgroundJobServer.stop();
            backgroundJobServer = null;
        }
    }

    @Override
    public DeadlineManager buildDeadlineManager(Configuration configuration) {
        StorageProvider storageProvider = new InMemoryStorageProvider();
        JobScheduler scheduler = new JobScheduler(storageProvider);
        JobRunrDeadlineManager manager = JobRunrDeadlineManager
                .builder()
                .jobScheduler(scheduler)
                .scopeAwareProvider(new ConfigurationScopeAwareProvider(configuration))
                .transactionManager(NoTransactionManager.INSTANCE)
                .spanFactory(configuration.spanFactory())
                .build();
        JobRunr.configure()
               .useJobActivator(new SimpleActivator(manager))
               .useStorageProvider(storageProvider)
               .useBackgroundJobServer(usingStandardBackgroundJobServerConfiguration().andPollIntervalInSeconds(5))
               .initialize();
        backgroundJobServer = JobRunr.getBackgroundJobServer();
        return manager;
    }

    @Test
    void shutdownInvokesSchedulerShutdown(@Mock ScopeAwareProvider scopeAwareProvider) {
        JobScheduler scheduler = spy(new JobScheduler(new InMemoryStorageProvider()));
        JobRunrDeadlineManager testSubject = JobRunrDeadlineManager.builder()
                                                                   .jobScheduler(scheduler)
                                                                   .scopeAwareProvider(scopeAwareProvider)
                                                                   .transactionManager(NoTransactionManager.INSTANCE)
                                                                   .build();

        testSubject.shutdown();

        verify(scheduler).shutdown();
    }

    @Test
    @Ignore("Currently cancel all within scope is not implemented")
    void deadlineCancellationWithinScopeOnAggregate() {
    }

    @Test
    @Ignore("Currently cancel all is not implemented")
    void deadlineCancelAllOnAggregateIsTracedCorrectly() {
    }

    @Test
    @Ignore("Currently cancel all within scope is not implemented")
    void deadlineCancellationWithinScopeOnSaga() {
    }

    @Test
    @Ignore("Currently cancel all is not implemented")
    void deadlineCancelAllOnSagaIsCorrectlyTraced() {
    }
}
