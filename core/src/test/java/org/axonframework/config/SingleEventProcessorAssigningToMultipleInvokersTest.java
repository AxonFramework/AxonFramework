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
        SagaConfiguration<Saga1> saga1Configuration = SagaConfiguration.trackingSagaManager(Saga1.class);
        SagaConfiguration<Saga2> saga2Configuration = SagaConfiguration.trackingSagaManager(Saga2.class, "processor1");
        SagaConfiguration<Saga3> saga3Configuration = SagaConfiguration.trackingSagaManager(Saga3.class);
        EventHandlingConfiguration eventHandlingConfiguration = new EventHandlingConfiguration()
                .registerEventHandler(config -> new EventHandler1());
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .registerModule(new EventProcessingConfiguration()
                                                                               .usingTrackingProcessors())
                                                       .registerModule(saga1Configuration)
                                                       .registerModule(saga2Configuration)
                                                       .registerModule(saga3Configuration)
                                                       .registerModule(eventHandlingConfiguration)
                                                       .buildConfiguration();

        assertNotNull(saga1Configuration.getProcessor());
        assertNotNull(saga2Configuration.getProcessor());
        assertNotNull(saga3Configuration.getProcessor());
        assertNotNull(configuration.eventProcessingConfiguration().eventProcessor("processor1").get());
        assertEquals(saga1Configuration.getProcessor(), saga2Configuration.getProcessor());
        assertEquals(saga1Configuration.getProcessor(),
                     configuration.eventProcessingConfiguration().eventProcessor("processor1").get());
        assertNotEquals(saga1Configuration.getProcessor(), saga3Configuration.getProcessor());
        assertNotEquals(saga2Configuration.getProcessor(), saga3Configuration.getProcessor());
        assertNotEquals(configuration.eventProcessingConfiguration().eventProcessor("processor1").get(),
                        saga3Configuration.getProcessor());
    }

    @Test
    public void testMultipleAssignmentsToSubscribingProcessor() {
        SagaConfiguration<Saga1> saga1Configuration = SagaConfiguration.subscribingSagaManager(Saga1.class);
        SagaConfiguration<Saga2> saga2Configuration = SagaConfiguration
                .subscribingSagaManager(Saga2.class, "processor1");
        SagaConfiguration<Saga3> saga3Configuration = SagaConfiguration.subscribingSagaManager(Saga3.class);
        EventHandlingConfiguration eventHandlingConfiguration = new EventHandlingConfiguration()
                .registerEventHandler(config -> new EventHandler1());
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .registerModule(saga1Configuration)
                                                       .registerModule(saga2Configuration)
                                                       .registerModule(saga3Configuration)
                                                       .registerModule(eventHandlingConfiguration)
                                                       .buildConfiguration();

        assertNotNull(saga1Configuration.getProcessor());
        assertNotNull(saga2Configuration.getProcessor());
        assertNotNull(saga3Configuration.getProcessor());
        assertNotNull(configuration.eventProcessingConfiguration().eventProcessor("processor1").get());
        assertEquals(saga1Configuration.getProcessor(), saga2Configuration.getProcessor());
        assertEquals(saga1Configuration.getProcessor(),
                     configuration.eventProcessingConfiguration().eventProcessor("processor1").get());
        assertNotEquals(saga1Configuration.getProcessor(), saga3Configuration.getProcessor());
        assertNotEquals(saga2Configuration.getProcessor(), saga3Configuration.getProcessor());
        assertNotEquals(configuration.eventProcessingConfiguration().eventProcessor("processor1").get(),
                        saga3Configuration.getProcessor());
    }

    @Test
    public void testMultipleAssignmentsWithProvidedProcessorName() {
        SagaConfiguration<Saga1> saga1Configuration = SagaConfiguration
                .subscribingSagaManager(Saga1.class, "myProcessor");
        SagaConfiguration<Saga2> saga2Configuration = SagaConfiguration.subscribingSagaManager(Saga2.class);
        SagaConfiguration<Saga3> saga3Configuration = SagaConfiguration
                .subscribingSagaManager(Saga3.class, "myProcessor");
        EventHandlingConfiguration eventHandlingConfiguration = new EventHandlingConfiguration()
                .registerEventHandler(config -> new EventHandler1());
        EventProcessingConfiguration epr = new EventProcessingConfiguration()
                .registerEventProcessor("myProcessor", (name, conf, eventHandlerInvoker) ->
                        new SubscribingEventProcessor(name, eventHandlerInvoker, conf.eventBus()));
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .registerModule(epr)
                                                       .registerModule(saga1Configuration)
                                                       .registerModule(saga2Configuration)
                                                       .registerModule(saga3Configuration)
                                                       .registerModule(eventHandlingConfiguration)
                                                       .buildConfiguration();

        assertNotNull(saga1Configuration.getProcessor());
        assertNotNull(saga2Configuration.getProcessor());
        assertNotNull(saga3Configuration.getProcessor());
        assertNotNull(configuration.eventProcessingConfiguration().eventProcessor("processor1").get());
        assertEquals(saga1Configuration.getProcessor(), saga3Configuration.getProcessor());
        assertEquals(saga2Configuration.getProcessor(),
                     configuration.eventProcessingConfiguration().eventProcessor("processor1").get());
        assertNotEquals(saga2Configuration.getProcessor(), saga3Configuration.getProcessor());
        assertNotEquals(configuration.eventProcessingConfiguration().eventProcessor("processor1").get(),
                        saga3Configuration.getProcessor());
    }

    @Test
    public void testProcessorGroupAssignment() {
        SagaConfiguration<Saga1> saga1Configuration = SagaConfiguration.subscribingSagaManager(Saga1.class);
        SagaConfiguration<Saga2> saga2Configuration = SagaConfiguration.subscribingSagaManager(Saga2.class,
                                                                                               "processor1");
        SagaConfiguration<Saga3> saga3Configuration = SagaConfiguration.subscribingSagaManager(Saga3.class);
        EventProcessingConfiguration epr = new EventProcessingConfiguration()
                .registerEventProcessor("myProcessor", (name, conf, eventHandlerInvoker) ->
                        new SubscribingEventProcessor(name, eventHandlerInvoker, conf.eventBus()))
                .assignProcessingGroup("processor1", "myProcessor");

        DefaultConfigurer.defaultConfiguration()
                         .registerModule(epr)
                         .registerModule(saga1Configuration)
                         .registerModule(saga2Configuration)
                         .registerModule(saga3Configuration)
                         .buildConfiguration();

        assertEquals("myProcessor", saga1Configuration.getProcessor().getName());
        assertEquals("myProcessor", saga2Configuration.getProcessor().getName());
        assertEquals("Saga3Processor", saga3Configuration.getProcessor().getName());
    }

    @Test
    public void testProcessorGroupAssignmentByRule() {
        SagaConfiguration<Saga1> saga1Configuration = SagaConfiguration.subscribingSagaManager(Saga1.class);
        SagaConfiguration<Saga2> saga2Configuration = SagaConfiguration.subscribingSagaManager(Saga2.class,
                                                                                               "processor1");
        SagaConfiguration<Saga3> saga3Configuration = SagaConfiguration.subscribingSagaManager(Saga3.class,
                                                                                               "processor1");
        EventProcessingConfiguration epr = new EventProcessingConfiguration()
                .registerEventProcessor("myProcessor", (name, conf, eventHandlerInvoker) ->
                        new SubscribingEventProcessor(name, eventHandlerInvoker, conf.eventBus()))
                .assignProcessingGroup(group -> "myProcessor");

        DefaultConfigurer.defaultConfiguration()
                         .registerModule(epr)
                         .registerModule(saga1Configuration)
                         .registerModule(saga2Configuration)
                         .registerModule(saga3Configuration)
                         .buildConfiguration();

        assertEquals("myProcessor", saga1Configuration.getProcessor().getName());
        assertEquals("myProcessor", saga2Configuration.getProcessor().getName());
        assertEquals("myProcessor", saga3Configuration.getProcessor().getName());
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
