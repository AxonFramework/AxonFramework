/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.configuration;

import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultComponentRegistryTest {

    private static final String INIT_STATE = "initial-state";
    private static final TestComponent TEST_COMPONENT = new TestComponent(INIT_STATE);

    private DefaultComponentRegistry testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultComponentRegistry();
    }

    @Test
    void registerComponentExposesRegisteredComponentUponBuild() {
        TestComponent testComponent = TEST_COMPONENT;
        testSubject.registerComponent(TestComponent.class, c -> testComponent);

        NewConfiguration config = testSubject.build(mock());

        assertEquals(testComponent, config.getComponent(TestComponent.class));
    }

    @Test
    void registerComponentExposesRegisteredComponentOnOptionalGet() {
        TestComponent testComponent = TEST_COMPONENT;
        testSubject.registerComponent(TestComponent.class, c -> testComponent);

        NewConfiguration config = testSubject.build(mock());

        Optional<TestComponent> result = config.getOptionalComponent(
                TestComponent.class);

        assertTrue(result.isPresent());
        assertEquals(testComponent, result.get());
    }

    @Test
    void getOptionalComponentResultsInEmptyOptionalForUnregisteredComponent() {
        Optional<TestComponent> result = testSubject.build(mock()).getOptionalComponent(TestComponent.class);

        assertFalse(result.isPresent());
    }

    @Test
    void canRegisterMultipleComponentsOfTheSameTypeForDifferentNames() {
        String testNameOne = "one";
        String testNameTwo = "two";
        TestComponent testComponentOne = new TestComponent(testNameOne);
        TestComponent testComponentTwo = new TestComponent(testNameTwo);
        testSubject.registerComponent(TestComponent.class,
                                      testNameOne,
                                      c -> testComponentOne).registerComponent(
                TestComponent.class,
                testNameTwo,
                c -> testComponentTwo);

        NewConfiguration config = testSubject.build(mock());

        assertEquals(testComponentOne, config.getComponent(TestComponent.class, testNameOne));
        assertEquals(testComponentTwo, config.getComponent(TestComponent.class, testNameTwo));
    }

    @Test
    void componentBuilderIsInvokedOnceUponRetrievalOfComponent() {
        AtomicInteger invocationCounter = new AtomicInteger(0);
        testSubject.registerComponent(TestComponent.class, "name", c -> {
            invocationCounter.incrementAndGet();
            return TEST_COMPONENT;
        });

        NewConfiguration config = testSubject.build(mock());

        assertEquals(0, invocationCounter.get());
        config.getComponent(TestComponent.class, "name");
        assertEquals(1, invocationCounter.get());
        config.getComponent(TestComponent.class, "name");
        assertEquals(1, invocationCounter.get());
    }

    @Test
    void registeringComponentsForTheSameTypeReplacesThePreviousComponentBuilder() {
        TestComponent testComponent = new TestComponent("replaced-component");
        TestComponent expectedComponent = new TestComponent("the-winner");
        testSubject.registerComponent(TestComponent.class, c -> testComponent)
                   .registerComponent(TestComponent.class, c -> expectedComponent);

        NewConfiguration config = testSubject.build(mock());

        assertNotEquals(testComponent, config.getComponent(TestComponent.class));
        assertEquals(expectedComponent, config.getComponent(TestComponent.class));
    }

    @Test
    void registeringComponentsForTheSameTypeAndNameReplacesThePreviousComponentBuilder() {
        TestComponent testComponent = new TestComponent("replaced-component");
        TestComponent expectedComponent = new TestComponent("the-winner");
        testSubject.registerComponent(TestComponent.class, "name", c -> testComponent)
                   .registerComponent(TestComponent.class,
                                      "name",
                                      c -> expectedComponent);

        NewConfiguration config = testSubject.build(mock());

        assertNotEquals(testComponent, config.getComponent(TestComponent.class, "name"));
        assertEquals(expectedComponent, config.getComponent(TestComponent.class, "name"));
    }

    @Test
    void registeringComponentsForTheSameTypeAndNameCombinationReplacesThePreviousComponentBuilder() {
        TestComponent testComponent = new TestComponent("replaced-component");
        TestComponent expectedComponent = new TestComponent("the-winner");
        testSubject.registerComponent(TestComponent.class, "name", c -> testComponent)
                   .registerComponent(TestComponent.class,
                                      "name",
                                      c -> expectedComponent);

        NewConfiguration config = testSubject.build(mock());

        assertNotEquals(testComponent, config.getComponent(TestComponent.class, "name"));
        assertEquals(expectedComponent, config.getComponent(TestComponent.class, "name"));
    }

    @Test
    void getComponentWithDefaultInvokesSupplierWhenThereIsNoRegisteredComponentForTheGivenClass() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        TestComponent defaultComponent = new TestComponent("default");
        TestComponent registeredComponent = TEST_COMPONENT;
        testSubject.registerComponent(TestComponent.class,
                                      "id",
                                      c -> registeredComponent);

        NewConfiguration config = testSubject.build(mock());

        TestComponent result = config.getComponent(TestComponent.class, "id", () -> {
            invoked.set(true);
            return defaultComponent;
        });

        assertFalse(invoked.get());
        assertNotEquals(defaultComponent, result);
        assertEquals(registeredComponent, result);

        invoked.set(false);
        result = config.getComponent(TestComponent.class, "non-registered-component", () -> {
            invoked.set(true);
            return defaultComponent;
        });

        assertTrue(invoked.get());
        assertEquals(defaultComponent, result);
        assertNotEquals(registeredComponent, result);
    }

    @Test
    void nestedConfigurationCanAccessComponentsFromParent() {
        testSubject.registerModule(new TestModule("test1")
                                           .componentRegistry(cr ->
                                                                      cr.registerComponent(String.class,
                                                                                           "child",
                                                                                           c -> c.getComponent(
                                                                                                   String.class,
                                                                                                   "parent")
                                                                                                   + "&child")
                                           ))
                   .registerComponent(String.class, "parent", c -> "parent");
        NewConfiguration actual = testSubject.build(mock());

        assertEquals("parent", actual.getComponent(String.class, "parent"));
        assertThrows(ComponentNotFoundException.class, () -> actual.getComponent(String.class, "child"));

        assertEquals("parent&child", actual.getModuleConfigurations().get(0).getComponent(String.class, "child"));
    }

    @Test
    void enhancersAreInvokedInOrder() {
        List<ConfigurationEnhancer> invokedEnhancers = new ArrayList<>();
        ConfigurationEnhancer enhancer1 = new RegisteringConfigurationEnhancer(invokedEnhancers, 0);
        ConfigurationEnhancer enhancer2 = new RegisteringConfigurationEnhancer(invokedEnhancers, 10);
        ConfigurationEnhancer enhancer3 = new RegisteringConfigurationEnhancer(invokedEnhancers, 100);
        testSubject.registerEnhancer(enhancer2)
                   .registerEnhancer(enhancer1)
                   .registerEnhancer(enhancer3);

        testSubject.build(mock());

        assertEquals(List.of(enhancer1, enhancer2, enhancer3), invokedEnhancers);
    }

    protected record TestComponent(String state) {

    }

    private static class RegisteringConfigurationEnhancer implements ConfigurationEnhancer {

        private final List<ConfigurationEnhancer> invokedEnhancers;
        private int order;

        public RegisteringConfigurationEnhancer(List<ConfigurationEnhancer> invokedEnhancers, int order) {
            this.invokedEnhancers = invokedEnhancers;
            this.order = order;
        }

        @Override
        public void enhance(@Nonnull ComponentRegistry configurer) {
            invokedEnhancers.add(this);
        }

        @Override
        public int order() {
            return order;
        }
    }

    private static class TestModule extends BaseModule<TestModule> {

        /**
         * Construct a base module with the given {@code name}.
         *
         * @param name The name of this module. Must not be {@code null}
         */
        public TestModule(@Nonnull String name) {
            super(name);
        }
    }
}