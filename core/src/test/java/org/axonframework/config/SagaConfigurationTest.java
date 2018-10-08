/*
 * Copyright (c) 2010-2017. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.config;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.config.SagaConfiguration.SagaConfigurer;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.LoggingErrorHandler;
import org.axonframework.eventhandling.saga.AnnotatedSagaManager;
import org.axonframework.eventhandling.saga.SagaRepository;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore;
import org.junit.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SagaConfigurationTest {

    @Test
    public void testNullChecksOnSagaConfigurer() {
        SagaConfigurer<Object> configurer = SagaConfiguration.forType(Object.class);
        assertConfigurerNullCheck(() -> configurer.type(null), "Saga type should be checked for null");
        assertConfigurerNullCheck(() -> configurer.processingGroup(null),
                                  "Processing group should be checked for null");
        assertConfigurerNullCheck(() -> configurer.storeBuilder(null), "Saga store builder should be checked for null");
        assertConfigurerNullCheck(() -> configurer.listenerInvocationHandler(null),
                                  "Saga listener invocation error handler should be checked for null");
        assertConfigurerNullCheck(() -> configurer.managerBuilder(null), "Saga manager should be checked for null");
        assertConfigurerNullCheck(() -> configurer.repositoryBuilder(null),
                                  "Saga repository should be checked for null");
    }

    @Test
    public void testDefaultConfiguration() {
        SagaConfiguration<Object> sagaConfiguration = SagaConfiguration.defaultConfiguration(Object.class);
        ListenerInvocationErrorHandler listenerInvocationErrorHandler = mock(ListenerInvocationErrorHandler.class);
        SagaStore store = mock(SagaStore.class);
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .registerModule(new EventProcessingModule())
                                                       .registerComponent(ListenerInvocationErrorHandler.class,
                                                                          c -> listenerInvocationErrorHandler)
                                                       .registerComponent(SagaStore.class, c -> store)
                                                       .buildConfiguration();
        sagaConfiguration.initialize(configuration);

        assertFalse(sagaConfiguration.processingGroup().isPresent());
        assertEquals(Object.class, sagaConfiguration.type());
        assertEquals(store, sagaConfiguration.store().get());
        assertEquals(listenerInvocationErrorHandler, sagaConfiguration.listenerInvocationErrorHandler().get());
    }

    @Test
    public void testCustomConfiguration() {
        LoggingErrorHandler loggingErrorHandler = new LoggingErrorHandler();
        SagaStore<Object> sagaStore = new InMemorySagaStore();
        SagaRepository<Object> repository = mock(SagaRepository.class);
        AnnotatedSagaManager<Object> manager = mock(AnnotatedSagaManager.class);
        String processingGroup = "myProcessingGroup";
        SagaConfiguration<Object> sagaConfiguration = SagaConfiguration.forType(Object.class)
                                                                       .processingGroup(processingGroup)
                                                                       .listenerInvocationHandler(c -> loggingErrorHandler)
                                                                       .storeBuilder(c -> sagaStore)
                                                                       .repositoryBuilder(c -> repository)
                                                                       .managerBuilder(c -> manager)
                                                                       .configure();
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .registerModule(new EventProcessingModule())
                                                       .buildConfiguration();
        sagaConfiguration.initialize(configuration);

        assertEquals(Object.class, sagaConfiguration.type());
        assertEquals(processingGroup, sagaConfiguration.processingGroup().get());
        assertEquals(manager, sagaConfiguration.manager().get());
        assertEquals(repository, sagaConfiguration.repository().get());
        assertEquals(sagaStore, sagaConfiguration.store().get());
        assertEquals(loggingErrorHandler, sagaConfiguration.listenerInvocationErrorHandler().get());
    }

    private void assertConfigurerNullCheck(Runnable r, String message) {
        try {
            r.run();
            fail(message);
        } catch (AxonConfigurationException ace) {
            // we expect this exception
        }
    }
}
