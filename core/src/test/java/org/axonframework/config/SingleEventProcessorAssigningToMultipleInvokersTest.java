/*
 * Copyright (c) 2010-2018. Axon Framework
 *
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

import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * Testing functionality of assigning different invokers (saga or event handlers) to the same event processor.
 *
 * @author Milan Savic
 */
public class SingleEventProcessorAssigningToMultipleInvokersTest {

    @Test
    public void testMultipleAssignmentsToTrackingProcessor() {
        SagaConfiguration<Saga1> saga1Configuration = SagaConfiguration.forType(Saga1.class)
                                                                       .configure();
        SagaConfiguration<Saga2> saga2Configuration = SagaConfiguration.forType(Saga2.class)
                                                                       .configure();
        SagaConfiguration<Saga3> saga3Configuration = SagaConfiguration.forType(Saga3.class)
                                                                       .configure();
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .registerEventHandler(config -> new EventHandler1())
                  .registerSagaConfiguration(saga1Configuration)
                  .registerSagaConfiguration(saga2Configuration)
                  .registerSagaConfiguration(saga3Configuration);

        Configuration configuration = configurer.buildConfiguration();

        assertNotNull(saga1Configuration.eventProcessor());
        assertNotNull(saga2Configuration.eventProcessor());
        assertNotNull(saga3Configuration.eventProcessor());
        assertNotNull(configuration.eventProcessingConfiguration().eventProcessor("processor1").get());
        assertEquals(saga1Configuration.eventProcessor(), saga2Configuration.eventProcessor());
        assertEquals(saga1Configuration.eventProcessor(),
                     configuration.eventProcessingConfiguration().eventProcessor("processor1").get());
        assertNotEquals(saga1Configuration.eventProcessor(), saga3Configuration.eventProcessor());
        assertNotEquals(saga2Configuration.eventProcessor(), saga3Configuration.eventProcessor());
        assertNotEquals(configuration.eventProcessingConfiguration().eventProcessor("processor1").get(),
                        saga3Configuration.eventProcessor());
    }

    @Test
    public void testMultipleAssignmentsToSubscribingProcessor() {
        SagaConfiguration<Saga1> saga1Configuration = SagaConfiguration.forType(Saga1.class)
                                                                       .configure();
        SagaConfiguration<Saga2> saga2Configuration = SagaConfiguration.forType(Saga2.class)
                                                                       .configure();
        SagaConfiguration<Saga3> saga3Configuration = SagaConfiguration.forType(Saga3.class)
                                                                       .configure();
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .usingSubscribingEventProcessors()
                  .registerEventHandler(config -> new EventHandler1())
                  .registerSagaConfiguration(saga1Configuration)
                  .registerSagaConfiguration(saga2Configuration)
                  .registerSagaConfiguration(saga3Configuration);
        Configuration configuration = configurer.buildConfiguration();

        assertNotNull(saga1Configuration.eventProcessor());
        assertNotNull(saga2Configuration.eventProcessor());
        assertNotNull(saga3Configuration.eventProcessor());
        assertNotNull(configuration.eventProcessingConfiguration().eventProcessor("processor1").get());
        assertEquals(saga1Configuration.eventProcessor(), saga2Configuration.eventProcessor());
        assertEquals(saga1Configuration.eventProcessor(),
                     configuration.eventProcessingConfiguration().eventProcessor("processor1").get());
        assertNotEquals(saga1Configuration.eventProcessor(), saga3Configuration.eventProcessor());
        assertNotEquals(saga2Configuration.eventProcessor(), saga3Configuration.eventProcessor());
        assertNotEquals(configuration.eventProcessingConfiguration().eventProcessor("processor1").get(),
                        saga3Configuration.eventProcessor());
    }

    @Test
    public void testMultipleAssignmentsWithProvidedProcessorName() {
        SagaConfiguration<Saga1> saga1Configuration = SagaConfiguration.forType(Saga1.class)
                                                                       .configure();
        SagaConfiguration<Saga2> saga2Configuration = SagaConfiguration.forType(Saga2.class)
                                                                       .configure();
        SagaConfiguration<Saga3> saga3Configuration = SagaConfiguration.forType(Saga3.class)
                                                                       .configure();
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .assignHandlerTypesMatching("processor1", clazz -> clazz.equals(Saga3.class))
                  .assignHandlerTypesMatching("processor1", clazz -> clazz.equals(Saga1.class))
                  .assignHandlerTypesMatching("someOtherProcessor", clazz -> clazz.equals(Saga2.class))
                  .assignProcessingGroup("processor1", "myProcessor")
                  .registerEventHandler(config -> new EventHandler1())
                  .registerSagaConfiguration(saga1Configuration)
                  .registerSagaConfiguration(saga2Configuration)
                  .registerSagaConfiguration(saga3Configuration)
                  .registerEventProcessor("myProcessor", (name, conf, eventHandlerInvoker) ->
                          new SubscribingEventProcessor(name, eventHandlerInvoker, conf.eventBus()));
        Configuration configuration = configurer.buildConfiguration();

        assertNotNull(saga1Configuration.eventProcessor());
        assertNotNull(saga2Configuration.eventProcessor());
        assertNotNull(saga3Configuration.eventProcessor());
        assertNotNull(configuration.eventProcessingConfiguration().eventProcessor("myProcessor").get());
        assertEquals(saga1Configuration.eventProcessor(), saga3Configuration.eventProcessor());
        assertEquals(saga2Configuration.eventProcessor(),
                     configuration.eventProcessingConfiguration().eventProcessor("someOtherProcessor").get());
        assertNotEquals(saga2Configuration.eventProcessor(), saga3Configuration.eventProcessor());
        assertEquals(configuration.eventProcessingConfiguration().eventProcessor("myProcessor").get(),
                        saga3Configuration.eventProcessor());
        assertNotEquals(configuration.eventProcessingConfiguration().eventProcessor("someOtherProcessor").get(),
                        saga3Configuration.eventProcessor());
    }

    @Test
    public void testProcessorGroupAssignment() {
        SagaConfiguration<Saga1> saga1Configuration = SagaConfiguration.forType(Saga1.class)
                                                                       .configure();
        SagaConfiguration<Saga2> saga2Configuration = SagaConfiguration.forType(Saga2.class)
                                                                       .configure();
        SagaConfiguration<Saga3> saga3Configuration = SagaConfiguration.forType(Saga3.class)
                                                                       .configure();
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .registerEventProcessor("myProcessor", (name, conf, eventHandlerInvoker) ->
                          new SubscribingEventProcessor(name, eventHandlerInvoker, conf.eventBus()))
                  .assignProcessingGroup("processor1", "myProcessor")
                  .registerSagaConfiguration(saga1Configuration)
                  .registerSagaConfiguration(saga2Configuration)
                  .registerSagaConfiguration(saga3Configuration);
        configurer.buildConfiguration();

        assertEquals("myProcessor", saga1Configuration.eventProcessor().getName());
        assertEquals("myProcessor", saga2Configuration.eventProcessor().getName());
        assertEquals("Saga3Processor", saga3Configuration.eventProcessor().getName());
    }

    @Test
    public void testProcessorGroupAssignmentByRule() {
        SagaConfiguration<Saga1> saga1Configuration = SagaConfiguration.forType(Saga1.class)
                                                                       .configure();
        SagaConfiguration<Saga2> saga2Configuration = SagaConfiguration.forType(Saga2.class)
                                                                       .configure();
        SagaConfiguration<Saga3> saga3Configuration = SagaConfiguration.forType(Saga3.class)
                                                                       .configure();
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .assignHandlerTypesMatching("myProcessor", clazz -> clazz.equals(Saga3.class))
                  .registerSagaConfiguration(saga1Configuration)
                  .registerSagaConfiguration(saga2Configuration)
                  .registerSagaConfiguration(saga3Configuration)
                  .registerEventProcessor("myProcessor", (name, conf, eventHandlerInvoker) ->
                          new SubscribingEventProcessor(name, eventHandlerInvoker, conf.eventBus()))
                  .assignProcessingGroup(group -> "myProcessor");

        configurer.buildConfiguration();

        assertEquals("myProcessor", saga1Configuration.eventProcessor().getName());
        assertEquals("myProcessor", saga2Configuration.eventProcessor().getName());
        assertEquals("myProcessor", saga3Configuration.eventProcessor().getName());
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
