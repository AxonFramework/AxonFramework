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

package org.axonframework.springboot.integration;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.eventhandling.AnnotationEventHandlerAdapter;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.spring.config.annotation.SpringBeanDependencyResolverFactory;
import org.axonframework.spring.config.annotation.SpringBeanParameterResolverFactory;
import org.axonframework.test.FixtureExecutionException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the functionality of Spring dependency resolution at the application context level. This covers both
 * {@link SpringBeanDependencyResolverFactory} and {@link SpringBeanParameterResolverFactory}, which behave differently
 * but exist within the same application context.
 *
 * @author Allard Buijze
 * @author Joel Feinstein
 * @see SpringBeanDependencyResolverFactory
 * @see SpringBeanParameterResolverFactory
 */
class SpringBeanResolverFactoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final EventMessage<Object> EVENT_MESSAGE = asEventMessage("Hi there");

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        COUNTER.set(0);
        testApplicationContext = new ApplicationContextRunner().withPropertyValues("axon.axonserver.enabled:false")
                                                               .withUserConfiguration(TestContext.class);
    }

    @Test
    void methodsAreProperlyInjected() {
        testApplicationContext.run(context -> {
            ParameterResolverFactory parameterResolver = context.getBean(ParameterResolverFactory.class);
            AnnotatedEventHandlerWithResources annotatedHandler =
                    context.getBean(AnnotatedEventHandlerWithResources.class);

            assertNotNull(annotatedHandler);
            new AnnotationEventHandlerAdapter(annotatedHandler, parameterResolver).handle(EVENT_MESSAGE);
            // make sure the invocation actually happened
            assertEquals(1, COUNTER.get());
        });
    }

    @Test
    void newInstanceIsCreatedEachTimePrototypeResourceIsInjected() {
        testApplicationContext.run(context -> {
            Object handler = context.getBean("prototypeResourceHandler");
            AnnotationEventHandlerAdapter adapter = new AnnotationEventHandlerAdapter(
                    handler, context.getBean(ParameterResolverFactory.class)
            );
            adapter.handle(EVENT_MESSAGE);
            adapter.handle(asEventMessage("Hello2"));
            assertEquals(2, COUNTER.get());
        });
    }

    @Test
    void methodsAreProperlyInjected_ErrorOnMissingParameterType() {
        testApplicationContext.run(context -> {
            Object bean = context.getBean("missingResourceHandler");
            ParameterResolverFactory parameterResolver = context.getBean(ParameterResolverFactory.class);

            // Generates a FixtureExecutionException due to the inclusion of axon-test,
            //  which includes the FixtureResourceParameterResolverFactory
            assertThrows(
                    FixtureExecutionException.class,
                    () -> new AnnotationEventHandlerAdapter(bean, parameterResolver).handle(EVENT_MESSAGE)
            );
        });
    }

    @Test
    void methodsAreProperlyInjected_NullableParameterType() {
        testApplicationContext.run(context -> {
            ParameterResolverFactory parameterResolver = context.getBean(ParameterResolverFactory.class);
            new AnnotationEventHandlerAdapter(
                    context.getBean("nullableResourceHandler"), parameterResolver
            ).handle(EVENT_MESSAGE);

            assertEquals(1, COUNTER.get());
        });
    }

    @Test
    void methodsAreProperlyInjected_ErrorOnDuplicateParameterType() {
        testApplicationContext.run(context -> {
            Object bean = context.getBean("duplicateResourceHandler");
            ParameterResolverFactory parameterResolver = context.getBean(ParameterResolverFactory.class);

            // Generates a FixtureExecutionException due to the inclusion of axon-test,
            //  which includes the FixtureResourceParameterResolverFactory
            assertThrows(
                    FixtureExecutionException.class,
                    () -> new AnnotationEventHandlerAdapter(bean, parameterResolver).handle(EVENT_MESSAGE)
            );
        });
    }

    @Test
    @DirtiesContext
    void methodsAreProperlyInjected_DuplicateParameterTypeWithPrimary() {
        testApplicationContext.run(context -> {
            Object bean = context.getBean("duplicateResourceHandlerWithPrimary");
            ParameterResolverFactory parameterResolver = context.getBean(ParameterResolverFactory.class);

            new AnnotationEventHandlerAdapter(bean, parameterResolver).handle(EVENT_MESSAGE);
            assertEquals(1, COUNTER.get());
        });
    }

    @Test
    @DirtiesContext
    void methodsAreProperlyInjected_DuplicateParameterTypeWithQualifier() {
        testApplicationContext.run(context -> {
            Object bean = context.getBean("duplicateResourceHandlerWithQualifier");
            ParameterResolverFactory parameterResolver = context.getBean(ParameterResolverFactory.class);

            new AnnotationEventHandlerAdapter(bean, parameterResolver).handle(EVENT_MESSAGE);
            assertEquals(1, COUNTER.get());
        });
    }

    @Test
    @DirtiesContext
    void methodsAreProperlyInjected_QualifierPrecedesPrimary() {
        testApplicationContext.run(context -> {
            Object bean = context.getBean("duplicateResourceHandlerWithQualifierAndPrimary");
            ParameterResolverFactory parameterResolver = context.getBean(ParameterResolverFactory.class);

            new AnnotationEventHandlerAdapter(bean, parameterResolver).handle(EVENT_MESSAGE);
            assertEquals(1, COUNTER.get());
        });
    }

    @Test
    @DirtiesContext
    void methodsAreProperlyInjected_DuplicateParameterWithAutowired() {
        testApplicationContext.run(context -> {
            Object handler = context.getBean("duplicateResourceHandlerWithAutowired");
            ParameterResolverFactory parameterResolver = context.getBean(ParameterResolverFactory.class);

            AnnotationEventHandlerAdapter adapter = new AnnotationEventHandlerAdapter(handler, parameterResolver);
            // Spring dependency resolution will resolve at time of execution
            assertThrows(NoUniqueBeanDefinitionException.class, () -> adapter.handle(EVENT_MESSAGE));
        });
    }

    @Test
    @DirtiesContext
    void methodsAreProperlyInjectedForDuplicateResourceHandlerWithAutowiredAndQualifier() {
        testApplicationContext.run(context -> {
            Object bean = context.getBean("duplicateResourceHandlerWithAutowiredAndQualifier");
            ParameterResolverFactory parameterResolver = context.getBean(ParameterResolverFactory.class);

            new AnnotationEventHandlerAdapter(bean, parameterResolver).handle(EVENT_MESSAGE);
            assertEquals(1, COUNTER.get());
        });
    }

    @Test
    void validatePrimaryBeans() {
        testApplicationContext.run(context -> {
            assertTrue(context.getBean("duplicateResourceWithPrimary1", DuplicateResourceWithPrimary.class)
                              .isPrimary());
            assertFalse(context.getBean("duplicateResourceWithPrimary2", DuplicateResourceWithPrimary.class)
                               .isPrimary());
        });
    }

    public interface DuplicateResourceWithPrimary {

        boolean isPrimary();
    }

    public interface DuplicateResource {

    }

    public interface DuplicateResourceWithQualifier {

    }

    @Configuration
    @EnableAutoConfiguration
    static class TestContext {

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
        public NullableResourceHandler nullableResourceHandler() {
            return new NullableResourceHandler();
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
        public DuplicateResourceHandlerWithAutowired duplicateResourceHandlerWithAutowired() {
            return new DuplicateResourceHandlerWithAutowired();
        }

        @Lazy
        @Bean
        public DuplicateResourceHandlerWithAutowiredAndQualifier duplicateResourceHandlerWithAutowiredAndQualifier() {
            return new DuplicateResourceHandlerWithAutowiredAndQualifier();
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

    @SuppressWarnings("unused")
    public static class MissingResourceHandler {

        @EventHandler
        public void handle(String message, ThisResourceReallyDoesntExist dataSource) {
            COUNTER.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    public static class NullableResourceHandler {

        @EventHandler
        public void handle(String message, @Autowired(required = false) ThisResourceReallyDoesntExist dataSource) {
            COUNTER.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    public static class DuplicateResourceHandler {

        @EventHandler
        public void handle(String message, DuplicateResource dataSource) {
            COUNTER.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    public static class DuplicateResourceHandlerWithPrimary {

        @EventHandler
        public void handle(String message, DuplicateResourceWithPrimary dataSource) {
            COUNTER.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    public static class DuplicateResourceHandlerWithQualifier {

        @EventHandler
        public void handle(String message, @Qualifier("qualifiedByName") DuplicateResourceWithQualifier resource) {
            COUNTER.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    public static class DuplicateResourceHandlerWithQualifierAndPrimary {

        @EventHandler
        public void handle(String message,
                           @Qualifier("duplicateResourceWithPrimary2") DuplicateResourceWithPrimary resource) {
            assertFalse(resource.isPrimary(), "expect the non-primary bean to be autowired here");
            COUNTER.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    public static class DuplicateResourceHandlerWithAutowired {

        @EventHandler
        public void handle(String message, @Autowired DuplicateResource resource) {
            COUNTER.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    public static class DuplicateResourceHandlerWithAutowiredAndQualifier {

        @EventHandler
        public void handle(String message,
                           @Autowired @Qualifier("qualifiedByName") DuplicateResourceWithQualifier resource) {
            COUNTER.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    public static class PrototypeResourceHandler {

        private PrototypeResource resource;

        @EventHandler
        public void handle(String message, PrototypeResource resource) {
            assertNotEquals(this.resource, this.resource = resource);
            COUNTER.incrementAndGet();
        }
    }

    public static class AnnotatedEventHandlerWithResources {

        @EventHandler
        public void handle(String message, CommandBus commandBus, EventBus eventBus) {
            assertNotNull(message);
            assertNotNull(commandBus);
            assertNotNull(eventBus);
            COUNTER.incrementAndGet();
        }
    }

    private interface ThisResourceReallyDoesntExist {

    }
}
