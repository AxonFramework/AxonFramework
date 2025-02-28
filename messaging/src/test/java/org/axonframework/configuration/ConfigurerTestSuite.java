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

import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite validating the common behavior of the {@link NewConfigurer}.
 *
 * @param <C> The {@link NewConfigurer} implementation under test.
 * @author Steven van Beelen
 */
public abstract class ConfigurerTestSuite<C extends NewConfigurer<C>> {

    protected static final String INIT_STATE = "initial-state";
    protected static final TestComponent TEST_COMPONENT = new TestComponent(INIT_STATE);

    protected C testSubject;

    @BeforeEach
    void setUp() {
        testSubject = testSubject();
    }

    /**
     * Construct the test subject of type {@code C} used for testing.
     *
     * @return The test subject of type {@code C} used for testing.
     */
    public abstract C testSubject();

    @Nested
    class ComponentRegistration {

        @Test
        void registerComponentExposesRegisteredComponentUponBuild() {
            TestComponent testComponent = TEST_COMPONENT;

            testSubject.registerComponent(TestComponent.class, c -> testComponent);

            NewConfiguration config = testSubject.build();

            assertEquals(testComponent, config.getComponent(TestComponent.class));
        }

        @Test
        void canRegisterMultipleComponentsOfTheSameTypeForDifferentNames() {
            String testNameOne = "one";
            String testNameTwo = "two";
            TestComponent testComponentOne = new TestComponent(testNameOne);
            TestComponent testComponentTwo = new TestComponent(testNameTwo);

            NewConfiguration config =
                    testSubject.registerComponent(TestComponent.class, testNameOne, c -> testComponentOne)
                               .registerComponent(TestComponent.class, testNameTwo, c -> testComponentTwo)
                               .build();

            assertEquals(testComponentOne, config.getComponent(TestComponent.class, testNameOne));
            assertEquals(testComponentTwo, config.getComponent(TestComponent.class, testNameTwo));
        }

        @Test
        void componentBuilderIsInvokedOnceUponRetrievalOfComponent() {
            AtomicInteger invocationCounter = new AtomicInteger(0);

            NewConfiguration config = testSubject.registerComponent(TestComponent.class, "name", c -> {
                                                     invocationCounter.incrementAndGet();
                                                     return TEST_COMPONENT;
                                                 })
                                                 .build();

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

            NewConfiguration config = testSubject.registerComponent(TestComponent.class, c -> testComponent)
                                                 .registerComponent(TestComponent.class, c -> expectedComponent)
                                                 .build();

            assertNotEquals(testComponent, config.getComponent(TestComponent.class));
            assertEquals(expectedComponent, config.getComponent(TestComponent.class));
        }

        @Test
        void registeringComponentsForTheSameTypeAndNameReplacesThePreviousComponentBuilder() {
            TestComponent testComponent = new TestComponent("replaced-component");
            TestComponent expectedComponent = new TestComponent("the-winner");

            NewConfiguration config =
                    testSubject.registerComponent(TestComponent.class, "name", c -> testComponent)
                               .registerComponent(TestComponent.class, "name", c -> expectedComponent)
                               .build();

            assertNotEquals(testComponent, config.getComponent(TestComponent.class, "name"));
            assertEquals(expectedComponent, config.getComponent(TestComponent.class, "name"));
        }

        @Test
        void registeringComponentsForTheSameTypeAndNameCombinationReplacesThePreviousComponentBuilder() {
            TestComponent testComponent = new TestComponent("replaced-component");
            TestComponent expectedComponent = new TestComponent("the-winner");

            NewConfiguration config =
                    testSubject.registerComponent(TestComponent.class, "name", c -> testComponent)
                               .registerComponent(TestComponent.class, "name", c -> expectedComponent)
                               .build();

            assertNotEquals(testComponent, config.getComponent(TestComponent.class, "name"));
            assertEquals(expectedComponent, config.getComponent(TestComponent.class, "name"));
        }
    }

    @Nested
    class ComponentRegistrationFailures {

        @Test
        void registerComponentThrowsNullPointerExceptionForNullType() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.registerComponent(null, c -> new Object()));
        }

        @Test
        void registerComponentThrowsNullPointerExceptionForNullName() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.registerComponent(Object.class, null, c -> new Object()));
        }

        @Test
        void registerComponentThrowsIllegalArgumentExceptionForEmptyName() {
            assertThrows(IllegalArgumentException.class,
                         () -> testSubject.registerComponent(Object.class, "", c -> new Object()));
        }

        @Test
        void registerComponentThrowsNullPointerExceptionForComponentBuilder() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class, () -> testSubject.registerComponent(TestComponent.class, null));
        }
    }

    @Nested
    class ComponentDecoration {

        @Test
        void registerDecoratorDecoratesOutcomeOfComponentBuilderInSpecifiedOrder() {
            String expectedState = TEST_COMPONENT.state() + "123";

            testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT)
                       .registerDecorator(TestComponent.class, 2,
                                          (c, delegate) -> new TestComponent(delegate.state + "3"))
                       .registerDecorator(TestComponent.class, 1,
                                          (c, delegate) -> new TestComponent(delegate.state + "2"))
                       .registerDecorator(TestComponent.class, 0,
                                          (c, delegate) -> new TestComponent(delegate.state + "1"));

            TestComponent result = testSubject.build()
                                              .getComponent(TestComponent.class);

            assertEquals(expectedState, result.state());
        }

        @Test
        void registerDecoratorReplacesPreviousDecoratorForReusedOrderDecoratesOutcomeOfComponentBuilderInSpecifiedOrder() {
            String expectedState = TEST_COMPONENT.state() + "bar";
            AtomicBoolean invoked = new AtomicBoolean(false);

            testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT)
                       .registerDecorator(TestComponent.class, 0,
                                          (c, delegate) -> {
                                              invoked.set(true);
                                              return new TestComponent(delegate.state + "foo");
                                          })
                       .registerDecorator(TestComponent.class, 0,
                                          (c, delegate) -> new TestComponent(delegate.state + "bar"));

            TestComponent result = testSubject.build()
                                              .getComponent(TestComponent.class);

            assertFalse(invoked.get());
            assertEquals(expectedState, result.state());
        }
    }

    @Nested
    class ComponentDecorationFailures {

        // TODO Discuss if we would want to throw an exception or whether we should simply store it if the register component comes in later
        @Test
        void registerDecoratorWithOrderThrowsIllegalArgumentExceptionForNonExistingComponentType() {
            assertThrows(IllegalArgumentException.class,
                         () -> testSubject.registerDecorator(TestComponent.class, 42, (c, delegate) -> delegate));
        }

        @Test
        void registerDecoratorWithOrderThrowsNullPointerExceptionForNullType() {
            testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT);

            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.registerDecorator(null, 42, (c, delegate) -> delegate));
        }

        @Test
        void registerDecoratorWithOrderThrowsNullPointerExceptionForNullName() {
            testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT);

            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.registerDecorator(Object.class, null, 42, (c, delegate) -> delegate));
        }

        @Test
        void registerDecoratorWithOrderThrowsNullPointerExceptionForNullComponentDecorator() {
            testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT);

            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.registerDecorator(TestComponent.class, 42, null));
        }
    }

    protected record TestComponent(String state) {

    }
}