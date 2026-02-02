/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.springboot.autoconfig;

import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.common.configuration.DecoratorDefinition;
import org.axonframework.messaging.core.unitofwork.UnitOfWork;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class demonstrating that {@link ConfigurationEnhancer} beans defined in a top-level
 * {@link TestConfiguration} class (explicitly imported) work reliably.
 * <p>
 * This serves as a comparison to {@link InnerTestConfigurationEnhancerTest} which uses
 * an inner {@code @TestConfiguration} and may fail intermittently due to a race condition.
 * <p>
 * This test should always pass, demonstrating the workaround for the bug.
 *
 * @author Mateusz Nowak
 * @see InnerTestConfigurationEnhancerTest for the failing case with inner configuration
 */
@SpringBootTest(
        classes = {
                TopLevelTestConfigurationEnhancerTest.MinimalAxonApplication.class,
                TopLevelEnhancerConfig.class
        },
        properties = {
                "axon.axonserver.enabled=false",
                "axon.eventstorage.jpa.polling-interval=0"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TopLevelTestConfigurationEnhancerTest {

    @Autowired
    private AxonConfiguration configuration;

    @Autowired
    private UnitOfWorkFactory unitOfWorkFactory;

    /**
     * Tests that a {@link ConfigurationEnhancer} defined in a top-level {@code @TestConfiguration}
     * properly decorates components resolved from {@link AxonConfiguration}.
     * <p>
     * This test is repeated 10 times to demonstrate that, unlike the inner configuration case,
     * the top-level configuration approach works reliably.
     * <p>
     * <b>Expected behavior:</b> All 10 iterations pass consistently (both before and after any fix).
     */
    @RepeatedTest(10)
    void topLevelTestConfigurationEnhancerShouldDecorateComponent() {
        // Resolve TestComponent from Configuration
        TestComponent component = configuration.getComponent(TestComponent.class);

        // Should be decorated by our enhancer
        assertThat(component)
                .as("Component should be decorated by ConfigurationEnhancer from top-level @TestConfiguration")
                .isInstanceOf(DecoratedTestComponent.class);

        // Verify the decoration works correctly
        assertThat(component.getValue())
                .as("Decorated component should prefix delegate value")
                .isEqualTo("decorated:default");
    }

    /**
     * Tests that a {@link ConfigurationEnhancer} defined in a top-level {@code @TestConfiguration}
     * properly decorates components resolved from {@link org.axonframework.messaging.core.unitofwork.ProcessingContext}.
     * <p>
     * This test creates a {@link UnitOfWork} and resolves the component inside the processing context,
     * which is a common pattern in Axon Framework command/query handlers.
     * <p>
     * <b>Expected behavior:</b> All 10 iterations pass consistently (both before and after any fix).
     */
    @RepeatedTest(10)
    void componentResolvedFromProcessingContextShouldBeDecorated() {
        UnitOfWork unitOfWork = unitOfWorkFactory.create();

        AtomicReference<TestComponent> componentRef = new AtomicReference<>();

        unitOfWork.executeWithResult(processingContext -> {
            TestComponent component = processingContext.component(TestComponent.class);
            componentRef.set(component);
            return CompletableFuture.completedFuture(null);
        }).join();

        TestComponent component = componentRef.get();

        // Should be decorated by our enhancer
        assertThat(component)
                .as("Component resolved from ProcessingContext should be decorated by ConfigurationEnhancer from top-level @TestConfiguration")
                .isInstanceOf(DecoratedTestComponent.class);

        // Verify the decoration works correctly
        assertThat(component.getValue())
                .as("Decorated component should prefix delegate value")
                .isEqualTo("decorated:default");
    }

    @EnableAutoConfiguration
    static class MinimalAxonApplication {
    }

    // Test interfaces and implementations (same as InnerTestConfigurationEnhancerTest)

    interface TestComponent {
        String getValue();
    }

    static class DefaultTestComponent implements TestComponent {
        @Override
        public String getValue() {
            return "default";
        }
    }

    static class DecoratedTestComponent implements TestComponent {
        private final TestComponent delegate;

        DecoratedTestComponent(TestComponent delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getValue() {
            return "decorated:" + delegate.getValue();
        }
    }
}

/**
 * Top-level {@code @TestConfiguration} containing a {@link ConfigurationEnhancer} bean.
 * <p>
 * This is the reliable approach - the enhancer is discovered during Spring context initialization
 * before {@code SpringComponentRegistry.initialize()} runs.
 */
@TestConfiguration
class TopLevelEnhancerConfig {

    @Bean
    ConfigurationEnhancer testComponentDecoratorEnhancer() {
        return registry -> registry.registerDecorator(
                DecoratorDefinition.forType(TopLevelTestConfigurationEnhancerTest.TestComponent.class)
                                   .with((config, name, delegate) ->
                                                 new TopLevelTestConfigurationEnhancerTest.DecoratedTestComponent(delegate))
                                   .order(Integer.MAX_VALUE)
        );
    }

    @Bean
    TopLevelTestConfigurationEnhancerTest.TestComponent testComponent() {
        return new TopLevelTestConfigurationEnhancerTest.DefaultTestComponent();
    }
}
