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

package org.axonframework.config;

import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing functionality of assigning different invokers (saga or event handlers) to the same event processor.
 *
 * @author Milan Savic
 */
class SingleEventProcessorAssigningToMultipleInvokersTest {

    @Test
    void multipleAssignmentsToTrackingProcessor() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .registerEventHandler(config -> new EventHandler1())
                  .registerSaga(Saga1.class, sc -> sc.configureSagaStore(c -> new InMemorySagaStore()))
                  .registerSaga(Saga2.class)
                  .registerSaga(Saga3.class);

        Configuration configuration = configurer.start();

        EventProcessingConfiguration processingConfiguration = configuration.eventProcessingConfiguration();
        EventProcessor saga1Processor = processingConfiguration.sagaEventProcessor(Saga1.class).orElse(null);
        EventProcessor saga2Processor = processingConfiguration.sagaEventProcessor(Saga2.class).orElse(null);
        EventProcessor saga3Processor = processingConfiguration.sagaEventProcessor(Saga3.class).orElse(null);

        assertNotNull(saga1Processor);
        assertNotNull(saga2Processor);
        assertNotNull(saga3Processor);
        Optional<EventProcessor> optionalProcessor = processingConfiguration.eventProcessor("processor1");
        assertTrue(optionalProcessor.isPresent());
        EventProcessor resultProcessor = optionalProcessor.get();
        assertNotNull(resultProcessor);
        assertEquals(saga1Processor, saga2Processor);
        assertEquals(saga1Processor, resultProcessor);
        assertNotEquals(saga1Processor, saga3Processor);
        assertNotEquals(saga2Processor, saga3Processor);
        assertNotEquals(saga3Processor, resultProcessor);
    }

    @Test
    void multipleAssignmentsToSubscribingProcessor() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .usingSubscribingEventProcessors()
                  .registerEventHandler(config -> new EventHandler1())
                  .registerSaga(Saga1.class)
                  .registerSaga(Saga2.class)
                  .registerSaga(Saga3.class);
        Configuration configuration = configurer.start();

        EventProcessingConfiguration processingConfiguration = configuration.eventProcessingConfiguration();
        EventProcessor saga1Processor = processingConfiguration.sagaEventProcessor(Saga1.class).orElse(null);
        EventProcessor saga2Processor = processingConfiguration.sagaEventProcessor(Saga2.class).orElse(null);
        EventProcessor saga3Processor = processingConfiguration.sagaEventProcessor(Saga3.class).orElse(null);

        assertNotNull(saga1Processor);
        assertNotNull(saga2Processor);
        assertNotNull(saga3Processor);
        Optional<EventProcessor> optionalProcessor = processingConfiguration.eventProcessor("processor1");
        assertTrue(optionalProcessor.isPresent());
        EventProcessor resultProcessor = optionalProcessor.get();
        assertNotNull(resultProcessor);
        assertEquals(saga1Processor, saga2Processor);
        assertEquals(saga1Processor, resultProcessor);
        assertNotEquals(saga1Processor, saga3Processor);
        assertNotEquals(saga2Processor, saga3Processor);
        assertNotEquals(resultProcessor, saga3Processor);
    }

    @Test
    void multipleAssignmentsWithProvidedProcessorName() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .assignHandlerTypesMatching("processor1", clazz -> clazz.equals(Saga3.class))
                  .assignHandlerTypesMatching("processor1", clazz -> clazz.equals(Saga1.class))
                  .assignHandlerTypesMatching("someOtherProcessor", clazz -> clazz.equals(Saga2.class))
                  .assignProcessingGroup("processor1", "myProcessor")
                  .registerEventHandler(config -> new EventHandler1())
                  .registerSaga(Saga1.class)
                  .registerSaga(Saga2.class)
                  .registerSaga(Saga3.class)
                  .registerEventProcessor("myProcessor", (name, conf, eventHandlerInvoker) ->
                          SubscribingEventProcessor.builder()
                                                   .name(name)
                                                   .eventHandlerInvoker(eventHandlerInvoker)
                                                   .messageSource(conf.eventBus())
                                                   .build()
                  );
        Configuration configuration = configurer.start();

        EventProcessingConfiguration processingConfiguration = configuration.eventProcessingConfiguration();
        EventProcessor saga1Processor = processingConfiguration.sagaEventProcessor(Saga1.class).orElse(null);
        EventProcessor saga2Processor = processingConfiguration.sagaEventProcessor(Saga2.class).orElse(null);
        EventProcessor saga3Processor = processingConfiguration.sagaEventProcessor(Saga3.class).orElse(null);
        assertNotNull(saga1Processor);
        assertNotNull(saga2Processor);
        assertNotNull(saga3Processor);
        Optional<EventProcessor> optionalProcessor = processingConfiguration.eventProcessor("myProcessor");
        assertTrue(optionalProcessor.isPresent());
        EventProcessor resultProcessor = optionalProcessor.get();
        assertNotNull(resultProcessor);
        assertEquals(saga1Processor, saga3Processor);
        optionalProcessor = processingConfiguration.eventProcessor("someOtherProcessor");
        assertTrue(optionalProcessor.isPresent());
        resultProcessor = optionalProcessor.get();
        assertEquals(saga2Processor, resultProcessor);
        assertNotEquals(saga2Processor, saga3Processor);
        assertEquals(saga3Processor, resultProcessor);
        assertNotEquals(saga3Processor, resultProcessor);
    }

    @Test
    void processorGroupAssignment() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .registerEventProcessor("myProcessor", (name, conf, eventHandlerInvoker) ->
                          SubscribingEventProcessor.builder()
                                                   .name(name)
                                                   .eventHandlerInvoker(eventHandlerInvoker)
                                                   .messageSource(conf.eventBus())
                                                   .build())
                  .assignProcessingGroup("processor1", "myProcessor")
                  .registerSaga(Saga1.class)
                  .registerSaga(Saga2.class)
                  .registerSaga(Saga3.class);
        Configuration configuration = configurer.start();

        EventProcessingConfiguration processingConfiguration = configuration.eventProcessingConfiguration();
        EventProcessor saga1Processor = processingConfiguration.sagaEventProcessor(Saga1.class).orElse(null);
        EventProcessor saga2Processor = processingConfiguration.sagaEventProcessor(Saga2.class).orElse(null);
        EventProcessor saga3Processor = processingConfiguration.sagaEventProcessor(Saga3.class).orElse(null);

        assertNotNull(saga1Processor);
        assertEquals("myProcessor", saga1Processor.getName());
        assertNotNull(saga2Processor);
        assertEquals("myProcessor", saga2Processor.getName());
        assertNotNull(saga3Processor);
        assertEquals("Saga3Processor", saga3Processor.getName());
    }

    @Test
    void processorGroupAssignmentByRule() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .assignHandlerTypesMatching("myProcessor", clazz -> clazz.equals(Saga3.class))
                  .registerSaga(Saga1.class)
                  .registerSaga(Saga2.class)
                  .registerSaga(Saga3.class)
                  .registerEventProcessor("myProcessor", (name, conf, eventHandlerInvoker) ->
                          SubscribingEventProcessor.builder()
                                                   .name(name)
                                                   .eventHandlerInvoker(eventHandlerInvoker)
                                                   .messageSource(conf.eventBus())
                                                   .build())
                  .assignProcessingGroup(group -> "myProcessor");

        Configuration configuration = configurer.start();

        EventProcessingConfiguration processingConfiguration = configuration.eventProcessingConfiguration();
        EventProcessor saga1Processor = processingConfiguration.sagaEventProcessor(Saga1.class).orElse(null);
        EventProcessor saga2Processor = processingConfiguration.sagaEventProcessor(Saga2.class).orElse(null);
        EventProcessor saga3Processor = processingConfiguration.sagaEventProcessor(Saga3.class).orElse(null);

        assertNotNull(saga1Processor);
        assertEquals("myProcessor", saga1Processor.getName());
        assertNotNull(saga2Processor);
        assertEquals("myProcessor", saga2Processor.getName());
        assertNotNull(saga3Processor);
        assertEquals("myProcessor", saga3Processor.getName());
    }

    @ProcessingGroup("processor1")
    private static class Saga1 {

    }

    @ProcessingGroup("processor1")
    private static class Saga2 {

    }

    private static class Saga3 {

    }

    @ProcessingGroup("processor1")
    private static class EventHandler1 {

    }
}
