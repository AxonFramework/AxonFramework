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

package org.axonframework.spring.config.annotation;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.eventhandling.AnnotationEventHandlerAdapter;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.axonframework.spring.config.AnnotationDriven;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@ContextConfiguration(classes = {SpringBeanParameterResolverFactoryTest.AppContext.class})
@RunWith(SpringJUnit4ClassRunner.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class SpringBeanParameterResolverFactoryTest {

    @Autowired
    private AnnotatedEventHandlerWithResources annotatedHandler;

    @Autowired
    private ApplicationContext applicationContext;

    private static final AtomicInteger counter = new AtomicInteger();

    @Autowired
    private ParameterResolverFactory parameterResolver;

    @Before
    public void setUp() {
        counter.set(0);
        assertTrue(applicationContext.getBean("duplicateResourceWithPrimary1", DuplicateResourceWithPrimary.class)
                                     .isPrimary());
        assertFalse(applicationContext.getBean("duplicateResourceWithPrimary2", DuplicateResourceWithPrimary.class)
                                      .isPrimary());
    }

    @Test
    public void testMethodsAreProperlyInjected() throws Exception {
        assertNotNull(annotatedHandler);
        new AnnotationEventHandlerAdapter(annotatedHandler, parameterResolver).handle(asEventMessage("Hello"));
        // make sure the invocation actually happened
        assertEquals(1, counter.get());
    }

    @Test
    public void testNewInstanceIsCreatedEachTimePrototypeResourceIsInjected() throws Exception {
        Object handler = applicationContext.getBean("prototypeResourceHandler");
        AnnotationEventHandlerAdapter adapter = new AnnotationEventHandlerAdapter(
                handler, applicationContext.getBean(ParameterResolverFactory.class)
        );
        adapter.handle(asEventMessage("Hello1"));
        adapter.handle(asEventMessage("Hello2"));
        assertEquals(2, counter.get());
    }

    @Test(expected = UnsupportedHandlerException.class)
    public void testMethodsAreProperlyInjected_ErrorOnMissingParameterType() {
        // this should generate an error
        new AnnotationEventHandlerAdapter(applicationContext.getBean("missingResourceHandler"), parameterResolver);
    }

    @Test(expected = UnsupportedHandlerException.class)
    public void testMethodsAreProperlyInjected_ErrorOnDuplicateParameterType() {
        // this should generate an error
        new AnnotationEventHandlerAdapter(applicationContext.getBean("duplicateResourceHandler"), parameterResolver);
    }

    @Test
    @DirtiesContext
    public void testMethodsAreProperlyInjected_DuplicateParameterTypeWithPrimary() throws Exception {
        // this should generate an error
        new AnnotationEventHandlerAdapter(applicationContext.getBean("duplicateResourceHandlerWithPrimary"),
                                          parameterResolver).handle(asEventMessage("Hi there"));

        assertEquals(1, counter.get());
    }

    @Test
    @DirtiesContext
    public void testMethodsAreProperlyInjected_DuplicateParameterTypeWithQualifier() throws Exception {
        new AnnotationEventHandlerAdapter(applicationContext.getBean("duplicateResourceHandlerWithQualifier"),
                                          parameterResolver).handle(asEventMessage("Hi there"));

        assertEquals(1, counter.get());
    }

    @Test
    @DirtiesContext
    public void testMethodsAreProperlyInjected_QualifierPrecedesPrimary() throws Exception {
        new AnnotationEventHandlerAdapter(
                applicationContext.getBean("duplicateResourceHandlerWithQualifierAndPrimary"),
                parameterResolver
        ).handle(asEventMessage("Hi there"));

        assertEquals(1, counter.get());
    }

    public interface DuplicateResourceWithPrimary {

        boolean isPrimary();
    }

    public interface DuplicateResource {

    }

    public interface DuplicateResourceWithQualifier {

    }

    @AnnotationDriven
    @Configuration
    public static class AppContext {

        @Bean(name = "annotatedHandler")
        public AnnotatedEventHandlerWithResources createHandler() {
            return new AnnotatedEventHandlerWithResources();
        }

        @Lazy
        @Bean
        public MissingResourceHandler missingResourceHandler() {
            return new MissingResourceHandler();
        }

        @Lazy
        @Bean
        public DuplicateResourceHandler duplicateResourceHandler() {
            return new DuplicateResourceHandler();
        }

        @Lazy
        @Bean
        public DuplicateResourceHandlerWithPrimary duplicateResourceHandlerWithPrimary() {
            return new DuplicateResourceHandlerWithPrimary();
        }

        @Lazy
        @Bean
        public DuplicateResourceHandlerWithQualifier duplicateResourceHandlerWithQualifier() {
            return new DuplicateResourceHandlerWithQualifier();
        }

        @Lazy
        @Bean
        public DuplicateResourceHandlerWithQualifierAndPrimary duplicateResourceHandlerWithQualifierAndPrimary() {
            return new DuplicateResourceHandlerWithQualifierAndPrimary();
        }

        @Lazy
        @Bean
        public PrototypeResourceHandler prototypeResourceHandler() {
            return new PrototypeResourceHandler();
        }

        @Bean
        public DuplicateResource duplicateResource1() {
            return new DuplicateResource() {
            };
        }

        @Bean
        public DuplicateResource duplicateResource2() {
            return new DuplicateResource() {
            };
        }

        @Primary
        @Bean
        public DuplicateResourceWithPrimary duplicateResourceWithPrimary1() {
            return () -> true;
        }

        @Bean
        public DuplicateResourceWithPrimary duplicateResourceWithPrimary2() {
            return () -> false;
        }

        @Bean
        public DuplicateResourceWithQualifier duplicateResourceWithQualifier1() {
            return new DuplicateResourceWithQualifier() {
            };
        }

        @Bean("qualifiedByName")
        public DuplicateResourceWithQualifier duplicateResourceWithQualifier2() {
            return new DuplicateResourceWithQualifier() {
            };
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public PrototypeResource prototypeResource() {
            return new PrototypeResource() {
            };
        }

        @Bean
        public CommandBus commandBus() {
            return SimpleCommandBus.builder().build();
        }

        @Bean
        public EventBus eventBus() {
            return SimpleEventBus.builder().build();
        }
    }

    public interface PrototypeResource {

    }

    public static class MissingResourceHandler {

        @EventHandler
        public void handle(String message, ThisResourceReallyDoesntExist dataSource) {
            counter.incrementAndGet();
        }
    }

    public static class DuplicateResourceHandler {

        @EventHandler
        public void handle(String message, DuplicateResource dataSource) {
            counter.incrementAndGet();
        }
    }

    public static class DuplicateResourceHandlerWithPrimary {

        @EventHandler
        public void handle(String message, DuplicateResourceWithPrimary dataSource) {
            counter.incrementAndGet();
        }
    }

    public static class DuplicateResourceHandlerWithQualifier {

        @EventHandler
        public void handle(String message, @Qualifier("qualifiedByName") DuplicateResourceWithQualifier resource) {
            counter.incrementAndGet();
        }
    }

    public static class DuplicateResourceHandlerWithQualifierAndPrimary {

        @EventHandler
        public void handle(String message,
                           @Qualifier("duplicateResourceWithPrimary2") DuplicateResourceWithPrimary resource) {
            assertFalse("expect the non-primary bean to be autowired here", resource.isPrimary());
            counter.incrementAndGet();
        }
    }

    public static class PrototypeResourceHandler {

        private PrototypeResource resource;

        @EventHandler
        public void handle(String message, PrototypeResource resource) {
            assertNotEquals(this.resource, this.resource = resource);
            counter.incrementAndGet();
        }
    }

    public static class AnnotatedEventHandlerWithResources {

        @EventHandler
        public void handle(String message, CommandBus commandBus, EventBus eventBus) {
            assertNotNull(message);
            assertNotNull(commandBus);
            assertNotNull(eventBus);
            counter.incrementAndGet();
        }
    }

    private interface ThisResourceReallyDoesntExist {

    }
}
