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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.lifecycle.LifecycleHandlerInvocationException;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.axonframework.utils.AssertUtils.assertWithin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite validating the workings of the lifecycle operations registered and invoked on
 * {@link ApplicationConfigurer} implementations and the resulting {@link AxonConfiguration}.
 * <p>
 * As such, operations like the {@link LifecycleRegistry#onStart(int, LifecycleHandler)},
 * {@link LifecycleRegistry#onShutdown(int, LifecycleHandler)}, {@link ApplicationConfigurer#start()},
 * {@link AxonConfiguration#start()} and {@link AxonConfiguration#shutdown()} will be tested.
 *
 * @author Steven van Beelen
 */
public abstract class ApplicationConfigurerTestSuite<C extends ApplicationConfigurer> {

    private static final String START_FAILURE_EXCEPTION_MESSAGE = "some start failure";
    private static final String INIT_STATE = "initial-state";
    protected static final TestComponent TEST_COMPONENT = new TestComponent(INIT_STATE);
    protected static final SpecificTestComponent SPECIFIC_TEST_COMPONENT = new SpecificTestComponent(INIT_STATE);

    protected C testSubject;

    @BeforeEach
    void setUp() {
        testSubject = createConfigurer();
    }

    /**
     * Builds the {@link ApplicationConfigurer} of type {@code C} to be used in this test suite for validating its
     * start-up and shutdown behavior.
     *
     * @return The {@link ApplicationConfigurer} of type {@code C} to be used in this test suite for validating its
     * start-up and shutdown behavior.
     */
    public abstract C createConfigurer();

    private AxonConfiguration buildConfiguration() {
        initialize(testSubject);
        return testSubject.build();
    }

    /**
     * Initializes the given {@code testSubject}.
     * <p>
     * Does nothing by default.
     *
     * @param testSubject The test subject of type {@code C} to initialize.
     */
    protected void initialize(C testSubject) {
        // No-op
    }

    /**
     * Boolean stating whether the {@link ApplicationConfigurer} under test supports {@link Component} overriding.
     *
     * @return {@code true} when {@link Component Components} can be overridden, {@code false} otherwise.
     */
    public boolean supportsOverriding() {
        return true;
    }

    /**
     * Boolean stating whether the {@link ApplicationConfigurer} under test supports the use of
     * {@link ComponentFactory ComponentFactories}.
     *
     * @return {@code true} when {@link ComponentFactory ComponentFactories} are supported, {@code false} otherwise.
     */
    public boolean supportsComponentFactories() {
        return true;
    }

    /**
     * Boolean stating whether the {@link ApplicationConfigurer} under test does its own lifecycle management through
     * the {@link LifecycleRegistry}.
     *
     * @return {@code true} when the {@link ApplicationConfigurer} under test controls the lifecycle management through
     * the {@link LifecycleRegistry}, {@code false} otherwise.
     */
    public boolean doesOwnLifecycleManagement() {
        return true;
    }

    protected static class TestComponent {

        private final String state;

        protected TestComponent(String state) {
            this.state = state;
        }

        protected String state() {
            return state;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestComponent that = (TestComponent) o;
            return Objects.equals(state, that.state);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(state);
        }
    }

    protected static class SpecificTestComponent extends TestComponent {

        protected SpecificTestComponent(String state) {
            super(state);
        }
    }

    protected static class TestModule extends BaseModule<TestModule> {

        protected TestModule(String name) {
            super(name);
        }
    }

    protected static class TestComponentFactory implements ComponentFactory<TestComponent> {

        private final String state;
        private final AtomicBoolean construct;
        private final AtomicBoolean shutdownInvoked;

        private TestComponentFactory(String state) {
            this(state, new AtomicBoolean(true));
        }

        private TestComponentFactory(String state, AtomicBoolean construct) {
            this(state, construct, new AtomicBoolean(false));
        }

        private TestComponentFactory(String state, AtomicBoolean construct, AtomicBoolean shutdownInvoked) {
            this.state = state;
            this.construct = construct;
            this.shutdownInvoked = shutdownInvoked;
        }

        @Nonnull
        @Override
        public Class<TestComponent> forType() {
            return TestComponent.class;
        }

        @Nonnull
        @Override
        public Optional<Component<TestComponent>> construct(@Nonnull String name,
                                                            @Nonnull Configuration config) {
            if (construct.get()) {
                return Optional.of(new InstantiatedComponentDefinition<>(
                        new Component.Identifier<>(forType(), name),
                        new TestComponent(state)
                ));
            }

            return Optional.empty();
        }

        @Override
        public void registerShutdownHandlers(@Nonnull LifecycleRegistry registry) {
            registry.onShutdown(() -> shutdownInvoked.set(true));
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            // Nothing to do here
        }
    }

    @Nested
    class ComponentRegistration {

        @Test
        void registerComponentForTypeExposesRegisteredComponentOnGet() {
            TestComponent testComponent = TEST_COMPONENT;
            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, c -> testComponent));

            Configuration config = buildConfiguration();

            assertEquals(testComponent, config.getComponent(TestComponent.class));
        }

        @Test
        void registerComponentForTypeExposesRegisteredComponentOnGetWhenAssignableFrom() {
            SpecificTestComponent testComponent = SPECIFIC_TEST_COMPONENT;
            testSubject.componentRegistry(
                    cr -> cr.registerComponent(SpecificTestComponent.class, c -> testComponent)
            );

            Configuration config = buildConfiguration();

            assertEquals(testComponent, config.getComponent(TestComponent.class));
        }

        @Test
        void registerComponentForTypeAndNameExposesRegisteredComponentOnGet() {
            TestComponent testComponent = TEST_COMPONENT;
            String testName = "some-name";
            testSubject.componentRegistry(
                    cr -> cr.registerComponent(TestComponent.class, testName, c -> testComponent)
            );

            Configuration config = buildConfiguration();

            assertEquals(testComponent, config.getComponent(TestComponent.class, testName));
        }

        @Test
        void registerComponentForTypeAndNameExposesRegisteredComponentWhenAssignableFrom() {
            SpecificTestComponent testComponent = SPECIFIC_TEST_COMPONENT;
            String testName = "some-name";
            testSubject.componentRegistry(
                    cr -> cr.registerComponent(SpecificTestComponent.class, testName, c -> testComponent)
            );

            Configuration config = buildConfiguration();

            assertEquals(testComponent, config.getComponent(TestComponent.class, testName));
        }

        @Test
        void registerComponentForTypeExposesRegisteredComponentOnOptionalGet() {
            TestComponent testComponent = TEST_COMPONENT;
            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, c -> testComponent));

            Configuration config = buildConfiguration();

            Optional<TestComponent> result = config.getOptionalComponent(TestComponent.class);

            assertTrue(result.isPresent());
            assertEquals(testComponent, result.get());
        }

        @Test
        void registerComponentForTypeExposesRegisteredComponentOnOptionalGetWhenAssignableFrom() {
            SpecificTestComponent testComponent = SPECIFIC_TEST_COMPONENT;
            testSubject.componentRegistry(
                    cr -> cr.registerComponent(SpecificTestComponent.class, c -> testComponent)
            );

            Configuration config = buildConfiguration();

            Optional<TestComponent> result = config.getOptionalComponent(TestComponent.class);

            assertTrue(result.isPresent());
            assertEquals(testComponent, result.get());
        }

        @Test
        void registerComponentForTypeAndNameExposesRegisteredComponentOnOptionalGet() {
            TestComponent testComponent = TEST_COMPONENT;
            String testName = "some-name";
            testSubject.componentRegistry(
                    cr -> cr.registerComponent(TestComponent.class, testName, c -> testComponent)
            );

            Configuration config = buildConfiguration();

            Optional<TestComponent> result = config.getOptionalComponent(TestComponent.class, testName);

            assertTrue(result.isPresent());
            assertEquals(testComponent, result.get());
        }

        @Test
        void registerComponentForTypeAndNameExposesRegisteredComponentOnOptionalGetWhenAssignableFrom() {
            SpecificTestComponent testComponent = SPECIFIC_TEST_COMPONENT;
            String testName = "some-name";
            testSubject.componentRegistry(
                    cr -> cr.registerComponent(SpecificTestComponent.class, testName, c -> testComponent)
            );

            Configuration config = buildConfiguration();

            Optional<TestComponent> result = config.getOptionalComponent(TestComponent.class, testName);

            assertTrue(result.isPresent());
            assertEquals(testComponent, result.get());
        }

        @Test
        void getOptionalComponentResultsInEmptyOptionalForUnregisteredComponent() {
            Optional<TestComponent> result = buildConfiguration().getOptionalComponent(TestComponent.class);

            assertFalse(result.isPresent());
        }

        @Test
        void canRegisterMultipleComponentsOfTheSameTypeForDifferentNames() {
            String testNameOne = "one";
            String testNameTwo = "two";
            TestComponent testComponentOne = new TestComponent(testNameOne);
            TestComponent testComponentTwo = new TestComponent(testNameTwo);
            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class,
                                                                     testNameOne,
                                                                     c -> testComponentOne).registerComponent(
                    TestComponent.class,
                    testNameTwo,
                    c -> testComponentTwo));

            Configuration config = buildConfiguration();

            assertEquals(testComponentOne, config.getComponent(TestComponent.class, testNameOne));
            assertEquals(testComponentTwo, config.getComponent(TestComponent.class, testNameTwo));
        }

        @Test
        void componentBuilderIsInvokedOnceUponRetrievalOfComponent() {
            AtomicInteger invocationCounter = new AtomicInteger(0);
            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, "name", c -> {
                invocationCounter.incrementAndGet();
                return TEST_COMPONENT;
            }));

            Configuration config = buildConfiguration();

            assertEquals(0, invocationCounter.get());
            config.getComponent(TestComponent.class, "name");
            assertEquals(1, invocationCounter.get());
            config.getComponent(TestComponent.class, "name");
            assertEquals(1, invocationCounter.get());
        }

        @Test
        void registeringComponentsForTheSameTypeReplacesThePreviousComponentBuilder() {
            Assumptions.assumeTrue(supportsOverriding(), "Ignore test since Component overriding is not supported.");

            TestComponent testComponent = new TestComponent("replaced-component");
            TestComponent expectedComponent = new TestComponent("the-winner");
            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, c -> testComponent)
                                                  .registerComponent(TestComponent.class, c -> expectedComponent));

            Configuration config = buildConfiguration();

            assertNotEquals(testComponent, config.getComponent(TestComponent.class));
            assertEquals(expectedComponent, config.getComponent(TestComponent.class));
        }

        @Test
        void registeringComponentsForTheSameTypeAndNameReplacesThePreviousComponentBuilder() {
            Assumptions.assumeTrue(supportsOverriding(), "Ignore test since Component overriding is not supported.");

            TestComponent testComponent = new TestComponent("replaced-component");
            TestComponent expectedComponent = new TestComponent("the-winner");
            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, "name", c -> testComponent)
                                                  .registerComponent(TestComponent.class,
                                                                     "name",
                                                                     c -> expectedComponent));

            Configuration config = buildConfiguration();

            assertNotEquals(testComponent, config.getComponent(TestComponent.class, "name"));
            assertEquals(expectedComponent, config.getComponent(TestComponent.class, "name"));
        }

        @Test
        void getComponentWithDefaultInvokesSupplierWhenThereIsNoRegisteredComponentForTheGivenClass() {
            AtomicBoolean invoked = new AtomicBoolean(false);
            TestComponent defaultComponent = new TestComponent("default");
            TestComponent registeredComponent = TEST_COMPONENT;
            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class,
                                                                     "id",
                                                                     c -> registeredComponent));

            Configuration config = buildConfiguration();

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
    }

    @Nested
    class ComponentRegistrationFailures {

        @Test
        void registerComponentThrowsNullPointerExceptionForNullType() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.componentRegistry(cr -> cr.registerComponent(null, c -> new Object())));
        }

        @Test
        void registerComponentThrowsIllegalArgumentExceptionForEmptyName() {
            assertThrows(IllegalArgumentException.class,
                         () -> testSubject.componentRegistry(cr -> cr.registerComponent(Object.class,
                                                                                        "",
                                                                                        c -> new Object())));
        }

        @Test
        void registerComponentThrowsNullPointerExceptionForComponentBuilder() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, null)));
        }

        @Test
        void duplicateRegistrationIsRejectedWhenOverrideModeIsReject() {
            testSubject.componentRegistry(cr -> cr.registerComponent(String.class, c -> "One")
                                                  .setOverridePolicy(OverridePolicy.REJECT));

            assertThrows(ComponentOverrideException.class,
                         () -> testSubject.componentRegistry(cr -> cr.registerComponent(String.class, c -> "Two")));
        }
    }

    @Nested
    class HasComponent {

        @Test
        void hasComponentForClass() {
            testSubject.componentRegistry(cr -> assertFalse(cr.hasComponent(TestComponent.class)));

            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, c -> TEST_COMPONENT));

            testSubject.componentRegistry(cr -> assertTrue(cr.hasComponent(TestComponent.class)));
        }

        @Test
        void hasComponentForClassAndSearchScope() {
            testSubject.componentRegistry(cr -> {
                assertFalse(cr.hasComponent(TestComponent.class, SearchScope.CURRENT));
                assertFalse(cr.hasComponent(TestComponent.class, SearchScope.ALL));
                assertFalse(cr.hasComponent(TestComponent.class, SearchScope.ANCESTORS));
            });

            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, c -> TEST_COMPONENT));

            testSubject.componentRegistry(cr -> {
                assertTrue(cr.hasComponent(TestComponent.class, SearchScope.CURRENT));
                assertTrue(cr.hasComponent(TestComponent.class, SearchScope.ALL));
                assertFalse(cr.hasComponent(TestComponent.class, SearchScope.ANCESTORS));
            });
        }

        @Test
        void hasComponentForClassAndSearchScopeInAncestorsOnly() {
            testSubject.componentRegistry(parentCr -> {
                assertFalse(parentCr.hasComponent(TestComponent.class, SearchScope.CURRENT));
                assertFalse(parentCr.hasComponent(TestComponent.class, SearchScope.ALL));
                assertFalse(parentCr.hasComponent(TestComponent.class, SearchScope.ANCESTORS));
            });

            testSubject.componentRegistry(
                    parentCr -> parentCr.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
            );

            testSubject.componentRegistry(parentCr -> parentCr.registerModule(
                    new TestModule("test-name").componentRegistry(
                            childCr -> childCr.registerEnhancer(registry -> {
                                assertFalse(registry.hasComponent(TestComponent.class, SearchScope.CURRENT));
                                assertTrue(registry.hasComponent(TestComponent.class, SearchScope.ALL));
                                assertTrue(registry.hasComponent(TestComponent.class, SearchScope.ANCESTORS));
                            })
                    )
            ));

            // Building the configuration triggers the ConfigurationEnhancer performing the has component checks.
            buildConfiguration();
        }

        @Test
        void hasComponentForClassAndName() {
            testSubject.componentRegistry(cr -> assertFalse(cr.hasComponent(TestComponent.class, "some-name")));

            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class,
                                                                     "some-name",
                                                                     c -> TEST_COMPONENT));

            testSubject.componentRegistry(cr -> assertTrue(cr.hasComponent(TestComponent.class, "some-name")));
        }

        @Test
        void hasComponentForClassNameAndSearchScope() {
            testSubject.componentRegistry(parentCr -> {
                assertFalse(parentCr.hasComponent(TestComponent.class, "some-name", SearchScope.CURRENT));
                assertFalse(parentCr.hasComponent(TestComponent.class, "some-name", SearchScope.ALL));
                assertFalse(parentCr.hasComponent(TestComponent.class, "some-name", SearchScope.ANCESTORS));
            });

            testSubject.componentRegistry(
                    parentCr -> parentCr.registerComponent(TestComponent.class, "some-name", c -> TEST_COMPONENT)
            );

            testSubject.componentRegistry(parentCr -> parentCr.registerModule(
                    new TestModule("test-name").componentRegistry(
                            childCr -> childCr.registerEnhancer(registry -> {
                                assertFalse(registry.hasComponent(TestComponent.class,
                                                                  "some-name",
                                                                  SearchScope.CURRENT));
                                assertTrue(registry.hasComponent(TestComponent.class, "some-name", SearchScope.ALL));
                                assertTrue(registry.hasComponent(TestComponent.class,
                                                                 "some-name",
                                                                 SearchScope.ANCESTORS
                                ));
                            })
                    )
            ));

            // Building the configuration triggers the ConfigurationEnhancer performing the has component checks.
            buildConfiguration();
        }

        @Test
        void hasComponentForClassNameAndSearchScopeInAncestorsOnly() {
            testSubject.componentRegistry(parentCr -> {
                assertFalse(parentCr.hasComponent(TestComponent.class, "some-name", SearchScope.CURRENT));
                assertFalse(parentCr.hasComponent(TestComponent.class, "some-name", SearchScope.ALL));
                assertFalse(parentCr.hasComponent(TestComponent.class, "some-name", SearchScope.ANCESTORS));
            });

            testSubject.componentRegistry(
                    parentCr -> parentCr.registerComponent(TestComponent.class, "some-name", c -> TEST_COMPONENT)
            );

            testSubject.componentRegistry(parentCr -> parentCr.registerModule(
                    new TestModule("test-name").componentRegistry(
                            childCr -> childCr.registerEnhancer(registry -> {
                                assertFalse(registry.hasComponent(TestComponent.class,
                                                                  "some-name",
                                                                  SearchScope.CURRENT));
                                assertTrue(registry.hasComponent(TestComponent.class,
                                                                 "some-name",
                                                                 SearchScope.ALL));
                                assertTrue(registry.hasComponent(TestComponent.class,
                                                                 "some-name",
                                                                 SearchScope.ANCESTORS));
                            })
                    )
            ));

            // Building the configuration triggers the ConfigurationEnhancer performing the has component checks.
            buildConfiguration();
        }
    }

    @Nested
    class ComponentRegistrationIfPresent {

        @Test
        void registersComponentValidatesForType() {
            // given...
            AtomicBoolean firstConstruction = new AtomicBoolean(false);
            AtomicBoolean secondConstruction = new AtomicBoolean(false);

            testSubject.componentRegistry(cr -> assertFalse(cr.hasComponent(TestComponent.class)));

            // when first registration if present...
            testSubject.componentRegistry(cr -> cr.registerIfNotPresent(TestComponent.class, c -> {
                firstConstruction.set(true);
                return TEST_COMPONENT;
            }));

            // then...
            testSubject.componentRegistry(cr -> assertTrue(cr.hasComponent(TestComponent.class)));
            // Retrieve the component, otherwise the builder is never invoked.
            buildConfiguration().getComponent(TestComponent.class);
            assertTrue(firstConstruction.get());

            // when second registration if present...
            testSubject.componentRegistry(cr -> cr.registerIfNotPresent(TestComponent.class, c -> {
                secondConstruction.set(true);
                return TEST_COMPONENT;
            }));

            // then...
            // Retrieve the component, otherwise the builder is never invoked.
            buildConfiguration().getComponent(TestComponent.class);
            assertFalse(secondConstruction.get());
        }

        @Test
        void registersComponentValidatesForTypeAndName() {
            // given...
            AtomicBoolean firstConstruction = new AtomicBoolean(false);
            AtomicBoolean secondConstruction = new AtomicBoolean(false);
            String testName = "some-name";

            testSubject.componentRegistry(cr -> assertFalse(cr.hasComponent(TestComponent.class, testName)));

            // when first registration if present...
            testSubject.componentRegistry(cr -> cr.registerIfNotPresent(TestComponent.class, testName, c -> {
                firstConstruction.set(true);
                return TEST_COMPONENT;
            }));

            // then...
            testSubject.componentRegistry(cr -> assertTrue(cr.hasComponent(TestComponent.class, testName)));
            // Retrieve the component, otherwise the builder is never invoked.
            buildConfiguration().getComponent(TestComponent.class, testName);
            assertTrue(firstConstruction.get());

            // when second registration if present...
            testSubject.componentRegistry(cr -> cr.registerIfNotPresent(TestComponent.class, testName, c -> {
                secondConstruction.set(true);
                return TEST_COMPONENT;
            }));

            // then...
            // Retrieve the component, otherwise the builder is never invoked.
            buildConfiguration().getComponent(TestComponent.class, testName);
            assertFalse(secondConstruction.get());
        }

        @Test
        void registersComponentValidatesForComponentDefinition() {
            // given...
            AtomicBoolean firstConstruction = new AtomicBoolean(false);
            AtomicBoolean secondConstruction = new AtomicBoolean(false);
            String testName = "some-name";

            testSubject.componentRegistry(cr -> assertFalse(cr.hasComponent(TestComponent.class, testName)));

            // when first registration if present...
            testSubject.componentRegistry(cr -> cr.registerIfNotPresent(
                    ComponentDefinition.ofTypeAndName(TestComponent.class, testName)
                                       .withBuilder(c -> {
                                           firstConstruction.set(true);
                                           return TEST_COMPONENT;
                                       })
            ));

            // then...
            testSubject.componentRegistry(cr -> assertTrue(cr.hasComponent(TestComponent.class, testName)));
            // Retrieve the component, otherwise the builder is never invoked.
            buildConfiguration().getComponent(TestComponent.class, testName);
            assertTrue(firstConstruction.get());

            // when second registration if present...
            testSubject.componentRegistry(cr -> cr.registerIfNotPresent(
                    ComponentDefinition.ofTypeAndName(TestComponent.class, testName)
                                       .withBuilder(c -> {
                                           secondConstruction.set(true);
                                           return TEST_COMPONENT;
                                       })
            ));

            // then...
            // Retrieve the component, otherwise the builder is never invoked.
            buildConfiguration().getComponent(TestComponent.class, testName);
            assertFalse(secondConstruction.get());
        }
    }

    @Nested
    class ComponentDecoration {

        @Test
        void registerDecoratorDecoratesOutcomeOfComponentBuilderInSpecifiedOrder() {
            String expectedState = TEST_COMPONENT.state() + "123";

            testSubject.componentRegistry(
                    cr -> cr.registerComponent(TestComponent.class, config -> TEST_COMPONENT)
                            .registerDecorator(TestComponent.class, 2,
                                               (c, name, delegate) -> new TestComponent(delegate.state + "3"))
                            .registerDecorator(TestComponent.class, 1,
                                               (c, name, delegate) -> new TestComponent(delegate.state + "2"))
                            .registerDecorator(TestComponent.class, "non-existent", 1,
                                               (c, name, delegate) -> new TestComponent(delegate.state + "999"))
                            .registerDecorator(TestComponent.class, 0,
                                               (c, name, delegate) -> new TestComponent(delegate.state + "1"))
            );

            TestComponent result = buildConfiguration().getComponent(TestComponent.class);

            assertEquals(expectedState, result.state());
        }

        @Test
        void registerDecoratorForTypeActsOnImplementationsOfComponents() {
            String expectedState = TEST_COMPONENT.state() + "1";

            testSubject.componentRegistry(
                    cr -> cr.registerComponent(SpecificTestComponent.class, config -> SPECIFIC_TEST_COMPONENT)
                            .registerDecorator(TestComponent.class, 0,
                                               (c, name, delegate) -> new SpecificTestComponent(delegate.state + "1"))
            );

            TestComponent result = buildConfiguration().getComponent(TestComponent.class);

            assertEquals(expectedState, result.state());
        }

        @Test
        void registerDecoratorForTypeAndNameActsOnImplementationsOfComponents() {
            String testName = "some-name";
            String expectedState = TEST_COMPONENT.state() + "1";

            testSubject.componentRegistry(
                    cr -> cr.registerComponent(SpecificTestComponent.class, testName, config -> SPECIFIC_TEST_COMPONENT)
                            .registerDecorator(TestComponent.class, testName, 0,
                                               (c, name, delegate) -> new SpecificTestComponent(delegate.state + "1"))
            );

            TestComponent result = buildConfiguration().getComponent(TestComponent.class, testName);

            assertEquals(expectedState, result.state());
        }

        /**
         * This test registers a SpecificTestComponent, making the Components identifier expect the type
         * SpecificTestComponent. Decorators can act on subtype, but should ensure that the decoration result is
         * assignable to the type used to register the original component. As when that's not done, a ClassCastException
         * is thrown to the user in their code.
         */
        @Test
        void registerDecoratorForTypeThrowsExceptionWithSpecificTraceWhenDecoratedTypeIsNotAssignableToRegisteredType() {
            String testName = "some-name";

            testSubject.componentRegistry(
                    cr -> cr.registerComponent(SpecificTestComponent.class, testName, config -> SPECIFIC_TEST_COMPONENT)
                            .registerDecorator(TestComponent.class, testName, 0,
                                               (c, name, delegate) -> new TestComponent(delegate.state + "1"))
            );

            Configuration config = buildConfiguration();

            assertThatThrownBy(() -> config.getComponent(TestComponent.class, testName)).hasStackTraceContaining(
                    "Make sure decorators return matching components, as component retrieval otherwise fails!"
            );
        }
    }

    @Nested
    class ComponentDecorationFailures {

        @Test
        void registerDecoratorThrowsNullPointerExceptionForNullType() {
            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, config -> TEST_COMPONENT));

            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.componentRegistry(
                                 cr -> cr.registerDecorator(null, 42, (c, name, delegate) -> delegate)
                         )
            );
        }

        @Test
        void registerDecoratorThrowsIllegalArgumentExceptionForNullName() {
            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, config -> TEST_COMPONENT));

            //noinspection DataFlowIssue
            assertThrows(IllegalArgumentException.class,
                         () -> testSubject.componentRegistry(
                                 cr -> cr.registerDecorator(Object.class, null, 42, (c, name, delegate) -> delegate)
                         )
            );
        }

        @Test
        void registerDecoratorThrowsNullPointerExceptionForNullComponentDecorator() {
            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, config -> TEST_COMPONENT));

            //noinspection DataFlowIssue
            assertThrows(
                    NullPointerException.class,
                    () -> testSubject.componentRegistry(cr -> cr.registerDecorator(TestComponent.class, 42, null))
            );
        }
    }

    @Nested
    class EnhancerRegistration {

        @Test
        void registerEnhancerThrowsNullPointerExceptionForNullEnhancer() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.componentRegistry(cr -> cr.registerEnhancer(null)));
        }

        @Test
        void registeredEnhancersAreInvokedDuringBuild() {
            AtomicBoolean invoked = new AtomicBoolean(false);

            testSubject.componentRegistry(cr -> cr.registerEnhancer(configurer -> invoked.set(true)));

            buildConfiguration();

            assertTrue(invoked.get());
        }

        @Test
        void registeredEnhancersAreInvokedDuringBuildOnlyOnce() {
            AtomicInteger counter = new AtomicInteger(0);

            testSubject.componentRegistry(cr -> cr.registerEnhancer(configurer -> counter.getAndIncrement()));
            // First build
            buildConfiguration();
            // Second build
            buildConfiguration();

            assertEquals(1, counter.get());
        }

        @Test
        void registeredEnhancersAreInvokedBasedOnInsertOrder() {
            //noinspection Convert2Lambda - Cannot be lambda, as spying doesn't work otherwise.
            ConfigurationEnhancer enhancerOne = spy(new ConfigurationEnhancer() {

                @Override
                public void enhance(@Nonnull ComponentRegistry registry) {
                    // Not important, so do nothing.
                }
            });
            //noinspection Convert2Lambda - Cannot be lambda, as spying doesn't work otherwise.
            ConfigurationEnhancer enhancerTwo = spy(new ConfigurationEnhancer() {
                @Override
                public void enhance(@Nonnull ComponentRegistry registry) {
                    // Not important, so do nothing.
                }
            });
            //noinspection Convert2Lambda - Cannot be lambda, as spying doesn't work otherwise.
            ConfigurationEnhancer enhancerThree = spy(new ConfigurationEnhancer() {

                @Override
                public void enhance(@Nonnull ComponentRegistry registry) {
                    // Not important, so do nothing.
                }
            });
            testSubject.componentRegistry(cr -> cr.registerEnhancer(enhancerOne).registerEnhancer(enhancerTwo)
                                                  .registerEnhancer(enhancerThree));

            buildConfiguration();

            InOrder enhancementOrder = inOrder(enhancerOne, enhancerTwo, enhancerThree);
            enhancementOrder.verify(enhancerOne).enhance(any());
            enhancementOrder.verify(enhancerTwo).enhance(any());
            enhancementOrder.verify(enhancerThree).enhance(any());
        }

        @Test
        void registeredEnhancersAreInvokedBasedOnDefinedOrder() {
            ConfigurationEnhancer enhancerWithLowOrder = spy(new ConfigurationEnhancer() {

                @Override
                public void enhance(@Nonnull ComponentRegistry registry) {
                    // Not important, so do nothing.
                }

                @Override
                public int order() {
                    return -42;
                }
            });

            //noinspection Convert2Lambda - Cannot be lambda, as spying doesn't work otherwise.
            ConfigurationEnhancer enhancerWithDefaultOrder = spy(new ConfigurationEnhancer() {
                @Override
                public void enhance(@Nonnull ComponentRegistry registry) {
                    // Not important, so do nothing.
                }
            });

            ConfigurationEnhancer enhancerWithHighOrder = spy(new ConfigurationEnhancer() {

                @Override
                public void enhance(@Nonnull ComponentRegistry registry) {
                    // Not important, so do nothing.
                }

                @Override
                public int order() {
                    return 42;
                }
            });
            testSubject.componentRegistry(cr -> cr.registerEnhancer(enhancerWithDefaultOrder)
                                                  .registerEnhancer(enhancerWithHighOrder)
                                                  .registerEnhancer(enhancerWithLowOrder));

            buildConfiguration();

            InOrder enhancementOrder = inOrder(enhancerWithLowOrder, enhancerWithDefaultOrder, enhancerWithHighOrder);
            enhancementOrder.verify(enhancerWithLowOrder).enhance(any());
            enhancementOrder.verify(enhancerWithDefaultOrder).enhance(any());
            enhancementOrder.verify(enhancerWithHighOrder).enhance(any());
        }

        @Test
        void registeredEnhancersCanAddComponents() {
            testSubject.componentRegistry(cr -> cr.registerEnhancer(configurer -> configurer.registerComponent(
                    TestComponent.class, c -> TEST_COMPONENT
            )));

            Configuration config = buildConfiguration();

            assertEquals(TEST_COMPONENT, config.getComponent(TestComponent.class));
        }

        @Test
        void registeredEnhancersCanDecorateComponents() {
            TestComponent expected = new TestComponent(TEST_COMPONENT.state() + "-decorated");
            ConfigurationEnhancer enhancer = configurer -> configurer.registerDecorator(
                    TestComponent.class, 0, (c, name, delegate) -> new TestComponent(delegate.state() + "-decorated")
            );
            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                                                  .registerEnhancer(enhancer));

            Configuration config = buildConfiguration();

            assertEquals(expected, config.getComponent(TestComponent.class));
        }

        @Test
        void registeredEnhancersCanReplaceComponents() {
            Assumptions.assumeTrue(supportsOverriding(), "Ignore test since Component overriding is not supported.");

            TestComponent expected = new TestComponent("replacement");

            testSubject.componentRegistry(
                    cr -> cr.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                            .registerEnhancer(configurer -> configurer.registerComponent(
                                    TestComponent.class,
                                    c -> expected
                            ))
            );

            Configuration config = buildConfiguration();

            assertNotEquals(TEST_COMPONENT, config.getComponent(TestComponent.class));
            assertEquals(expected, config.getComponent(TestComponent.class));
        }

        @Test
        void registeredEnhancersCanReplaceComponentsConditionally() {
            TestComponent expected = new TestComponent("conditional");

            testSubject.componentRegistry(cr -> cr.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                                                  .registerEnhancer(configurer -> {
                                                      if (configurer.hasComponent(TestComponent.class)) {
                                                          configurer.registerComponent(TestComponent.class,
                                                                                       "conditional",
                                                                                       c -> expected);
                                                      }
                                                  }));

            Configuration config = buildConfiguration();

            assertEquals(TEST_COMPONENT, config.getComponent(TestComponent.class));
            assertEquals(expected, config.getComponent(TestComponent.class, "conditional"));
        }

        @Test
        void registeringSameEnhancerTypeSeveralTimesWillReplacePreviousRegistration() {
            AtomicBoolean firstRegistration = new AtomicBoolean(false);
            AtomicBoolean secondRegistration = new AtomicBoolean(false);
            AtomicBoolean thirdRegistration = new AtomicBoolean(false);

            testSubject.componentRegistry(
                    cr -> cr.registerEnhancer(new TestConfigurationEnhancer(firstRegistration))
                            .registerEnhancer(new TestConfigurationEnhancer(secondRegistration))
                            .registerEnhancer(new TestConfigurationEnhancer(thirdRegistration))
            );

            buildConfiguration();

            assertFalse(firstRegistration.get());
            assertFalse(secondRegistration.get());
            assertTrue(thirdRegistration.get());
        }

        record TestConfigurationEnhancer(AtomicBoolean invoked) implements ConfigurationEnhancer {

            @Override
            public void enhance(@Nonnull ComponentRegistry registry) {
                invoked.set(true);
            }
        }
    }

    @Nested
    class ModuleRegistration {

        @Test
        void registerModuleThrowsNullPointerExceptionForNullModuleBuilder() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.componentRegistry(cr -> cr.registerModule(null)));
        }

        @Test
        void registerModuleExposesModulesConfigurationsUponBuild() {
            testSubject.componentRegistry(cr -> cr.registerModule(new TestModule("one"))
                                                  .registerModule(new TestModule("two")));

            AxonConfiguration configuration = buildConfiguration();
            List<Configuration> result = configuration.getModuleConfigurations();

            assertEquals(2, result.size());

            assertTrue(configuration.getModuleConfiguration("one").isPresent());
            assertTrue(configuration.getModuleConfiguration("two").isPresent());
            assertFalse(configuration.getModuleConfiguration("three").isPresent());
        }

        @Test
        void registeringModuleWithExistingNameIsRejected() {
            testSubject.componentRegistry(cr -> cr.registerModule(new TestModule("one"))
                                                  .registerModule(new TestModule("two")));

            assertThrows(DuplicateModuleRegistrationException.class,
                         () -> testSubject.componentRegistry(cr -> cr.registerModule(new TestModule("two"))));
        }

        @Test
        void canRetrieveComponentsFromModuleAndParentOnly() {
            TestComponent rootComponent = new TestComponent("root");
            TestComponent levelOneModuleComponent = new TestComponent("root-one");
            TestComponent levelTwoModuleComponent = new TestComponent("root-two");

            testSubject.componentRegistry(
                    cr -> cr.registerComponent(TestComponent.class, "root", rootConfig -> rootComponent)
                            .registerModule(new TestModule("one").componentRegistry(
                                    mcr -> mcr.registerComponent(
                                                      TestComponent.class, "one",
                                                      c -> c.getOptionalComponent(TestComponent.class, "root")
                                                            .map(delegate -> new TestComponent(delegate.state + "-one"))
                                                            .orElseThrow()
                                              )
                                              .registerModule(
                                                      new TestModule("two").componentRegistry(
                                                              cr2 -> cr2.registerComponent(
                                                                      TestComponent.class, "two",
                                                                      c -> c.getOptionalComponent(
                                                                                    TestComponent.class, "root"
                                                                            )
                                                                            .map(delegate -> new TestComponent(
                                                                                    delegate.state + "-two"
                                                                            ))
                                                                            .orElseThrow()))))
                            )
            );

            // Root configurer outcome only has own components.
            Configuration rootConfig = buildConfiguration();
            assertEquals(rootComponent, rootConfig.getComponent(TestComponent.class, "root"));
            assertFalse(rootConfig.getOptionalComponent(TestComponent.class, "one").isPresent());
            assertFalse(rootConfig.getOptionalComponent(TestComponent.class, "two").isPresent());
            // Level one module outcome has own components and access to parent.
            List<Configuration> levelOneConfigurations = rootConfig.getModuleConfigurations();
            assertEquals(1, levelOneConfigurations.size());
            Configuration levelOneConfig = levelOneConfigurations.getFirst();
            assertTrue(levelOneConfig.getOptionalComponent(TestComponent.class, "root").isPresent());
            assertEquals(levelOneModuleComponent, levelOneConfig.getComponent(TestComponent.class, "one"));
            assertFalse(levelOneConfig.getOptionalComponent(TestComponent.class, "two").isPresent());
            // Level two module outcome has own components and access to parent, and parent's parent.
            List<Configuration> levelTwoConfigurations = levelOneConfig.getModuleConfigurations();
            assertEquals(1, levelTwoConfigurations.size());
            Configuration levelTwoConfig = levelTwoConfigurations.getFirst();
            assertTrue(levelTwoConfig.getOptionalComponent(TestComponent.class, "root").isPresent());
            assertTrue(levelTwoConfig.getOptionalComponent(TestComponent.class, "one").isPresent());
            assertEquals(levelTwoModuleComponent, levelTwoConfig.getComponent(TestComponent.class, "two"));
        }

        @Test
        void cannotRetrieveComponentsRegisteredFromModulesRegisteredOnTheSameLevel() {
            TestComponent rootComponent = new TestComponent("root");
            TestComponent leftModuleComponent = new TestComponent("left");
            TestComponent rightModuleComponent = new TestComponent("right");
            testSubject.componentRegistry(
                    cr -> cr.registerComponent(TestComponent.class, "root", rootConfig -> rootComponent)
                            .registerModule(new TestModule("left").componentRegistry(
                                    mcr -> mcr.registerComponent(TestComponent.class, "left", c -> leftModuleComponent)
                            ))
                            .registerModule(new TestModule("right").componentRegistry(
                                    mcr -> mcr.registerComponent(TestComponent.class, "right",
                                                                 c -> rightModuleComponent)
                            ))
            );

            // Root configurer outcome only has own components.
            Configuration rootConfig = buildConfiguration();
            assertEquals(rootComponent, rootConfig.getComponent(TestComponent.class, "root"));
            assertFalse(rootConfig.getOptionalComponent(TestComponent.class, "one").isPresent());
            assertFalse(rootConfig.getOptionalComponent(TestComponent.class, "two").isPresent());
            List<Configuration> levelOneConfigurations = rootConfig.getModuleConfigurations();
            assertEquals(2, levelOneConfigurations.size());
            // Left module can access own components and parent, not its siblings.
            Configuration leftConfig = levelOneConfigurations.getFirst();
            assertTrue(leftConfig.getOptionalComponent(TestComponent.class, "root").isPresent());
            assertEquals(leftModuleComponent, leftConfig.getComponent(TestComponent.class, "left"));
            assertFalse(leftConfig.getOptionalComponent(TestComponent.class, "right").isPresent());
            // Right module can access own components and parent, not its siblings,
            Configuration rightConfig = levelOneConfigurations.get(1);
            assertTrue(rightConfig.getOptionalComponent(TestComponent.class, "root").isPresent());
            assertEquals(rightModuleComponent, rightConfig.getComponent(TestComponent.class, "right"));
            assertFalse(rightConfig.getOptionalComponent(TestComponent.class, "left").isPresent());
        }

        @Test
        void decoratingOnlyOccursOnTheModuleLevelItIsInvokedOn() {
            String expectedRootComponentState = "root-decorated-by-root";
            String expectedLevelOneComponentState = "level-one-decorated-by-level-one";
            String expectedLevelTwoComponentState = "level-two-decorated-by-level-two";
            testSubject.componentRegistry(
                    cr -> cr.registerComponent(TestComponent.class,
                                               rootConfig -> new TestComponent("root"))
                            .registerDecorator(
                                    TestComponent.class, 0,
                                    (rootConfig, name, delegate) -> new TestComponent(
                                            delegate.state() + "-decorated-by-root"
                                    )
                            )
                            .registerModule(
                                    new TestModule("level-one").componentRegistry(
                                            mcr -> mcr.registerComponent(
                                                              TestComponent.class,
                                                              c -> new TestComponent("level-one")
                                                      )
                                                      .registerDecorator(
                                                              TestComponent.class, 0,
                                                              (config, name, delegate) -> new TestComponent(
                                                                      delegate.state() + "-decorated-by-level-one"
                                                              )
                                                      )
                                                      .registerModule(
                                                              new TestModule("level-two").componentRegistry(
                                                                      emcr -> emcr.registerComponent(
                                                                                          TestComponent.class,
                                                                                          config -> new TestComponent(
                                                                                                  "level-two"
                                                                                          )
                                                                                  )
                                                                                  .registerDecorator(
                                                                                          TestComponent.class, 0,
                                                                                          (config, name, delegate) -> new TestComponent(
                                                                                                  delegate.state()
                                                                                                          + "-decorated-by-level-two")
                                                                                  )
                                                              )
                                                      )
                                    )
                            )
            );

            // Check decoration on root level.
            Configuration root = buildConfiguration();
            assertEquals(expectedRootComponentState, root.getComponent(TestComponent.class).state());
            assertNotEquals(expectedLevelOneComponentState, root.getComponent(TestComponent.class).state());
            assertNotEquals(expectedLevelTwoComponentState, root.getComponent(TestComponent.class).state());
            // Check decoration on level one.
            List<Configuration> rootModuleConfigs = root.getModuleConfigurations();
            assertEquals(1, rootModuleConfigs.size());
            Configuration levelOne = rootModuleConfigs.getFirst();
            assertNotEquals(expectedRootComponentState, levelOne.getComponent(TestComponent.class).state());
            assertEquals(expectedLevelOneComponentState, levelOne.getComponent(TestComponent.class).state());
            assertNotEquals(expectedLevelTwoComponentState, levelOne.getComponent(TestComponent.class).state());
            // Check decoration on level two.
            List<Configuration> levelOneConfigs = levelOne.getModuleConfigurations();
            assertEquals(1, levelOneConfigs.size());
            Configuration levelTwo = levelOneConfigs.getFirst();
            assertNotEquals(expectedRootComponentState, levelTwo.getComponent(TestComponent.class).state());
            assertNotEquals(expectedLevelOneComponentState, levelTwo.getComponent(TestComponent.class).state());
            assertEquals(expectedLevelTwoComponentState, levelTwo.getComponent(TestComponent.class).state());
        }

        @Test
        void getComponentWithDefaultChecksCurrentModuleAndParent() {
            AtomicBoolean invoked = new AtomicBoolean(false);
            TestComponent defaultComponent = new TestComponent("default");
            TestComponent registeredComponent = TEST_COMPONENT;
            testSubject.componentRegistry(
                    cr -> cr.registerModule(new TestModule("test-module").componentRegistry(
                            mcr -> mcr.registerComponent(TestComponent.class, "id", c -> registeredComponent))
                    )
            );

            Configuration rootConfig = buildConfiguration();

            TestComponent result = rootConfig.getComponent(TestComponent.class, "id", () -> {
                invoked.set(true);
                return defaultComponent;
            });

            assertTrue(invoked.get());
            assertEquals(defaultComponent, result);
            assertNotEquals(registeredComponent, result);

            invoked.set(false);
            List<Configuration> levelOneConfigs = rootConfig.getModuleConfigurations();
            assertEquals(1, levelOneConfigs.size());
            Configuration levelOneConfig = levelOneConfigs.getFirst();
            result = levelOneConfig.getComponent(TestComponent.class, "id", () -> {
                invoked.set(true);
                return defaultComponent;
            });

            assertFalse(invoked.get());
            assertNotEquals(defaultComponent, result);
            assertEquals(registeredComponent, result);
        }
    }

    @Nested
    class FactoryRegistration {

        @Test
        void factoryIsNotConsultedWhenComponentForTypeAndNameIsAlreadyPresent() {
            Assumptions.assumeTrue(supportsComponentFactories(),
                                   "Ignore test since ComponentFactories are not supported.");

            TestComponent expectedComponent = new TestComponent("state");
            TestComponent expectedNamedComponent = new TestComponent("named");
            TestComponentFactory testFactory = spy(new TestComponentFactory("constructed"));

            testSubject.componentRegistry(
                    registry -> registry.registerComponent(TestComponent.class, c -> expectedComponent)
                                        .registerComponent(TestComponent.class, "name", c -> expectedNamedComponent)
                                        .registerFactory(testFactory)
            );

            AxonConfiguration config = buildConfiguration();

            assertEquals(expectedComponent, config.getComponent(TestComponent.class));
            assertEquals(expectedNamedComponent, config.getComponent(TestComponent.class, "name"));
            verify(testFactory).registerShutdownHandlers(any());
            verifyNoMoreInteractions(testFactory);
        }

        @Test
        void factoryIsConsultedOnceWhenThereIsNoComponentForTypeAndName() {
            Assumptions.assumeTrue(supportsComponentFactories(),
                                   "Ignore test since ComponentFactories are not supported.");

            String expectedState = "constructed";
            TestComponentFactory testFactory = spy(new TestComponentFactory(expectedState));

            testSubject.componentRegistry(registry -> registry.registerFactory(testFactory));

            AxonConfiguration config = buildConfiguration();

            TestComponent resultComponent = config.getComponent(TestComponent.class, "first-instance");
            assertEquals(expectedState, resultComponent.state());
            // Second retrieval should return same object since first invocation should register the object.
            assertSame(resultComponent, config.getComponent(TestComponent.class, "first-instance"));
            verify(testFactory, times(1)).construct(any(), any());
            assertNotSame(resultComponent, config.getComponent(TestComponent.class, "second-instance"));
            verify(testFactory, times(2)).construct(any(), any());
        }

        @Test
        void factoryIsConsultedButMayReturnNothingWhenThereIsNoComponentForTypeAndName() {
            Assumptions.assumeTrue(supportsComponentFactories(),
                                   "Ignore test since ComponentFactories are not supported.");

            String expectedState = "constructed";
            TestComponentFactory testFactory = spy(new TestComponentFactory(expectedState, new AtomicBoolean(false)));

            testSubject.componentRegistry(registry -> registry.registerFactory(testFactory));

            AxonConfiguration config = buildConfiguration();

            assertFalse(config.getOptionalComponent(TestComponent.class).isPresent());
            assertFalse(config.getOptionalComponent(TestComponent.class).isPresent());
            assertFalse(config.getOptionalComponent(TestComponent.class, "name").isPresent());
            assertFalse(config.getOptionalComponent(TestComponent.class, "name").isPresent());
            // The ComponentFactory invocation is skipped when no name is given. Hence why we check for two invocations!
            verify(testFactory, times(2)).construct(any(), any());
        }

        @Test
        void getOrDefaultConsultsFactoryBeforeInvokingDefaultSupplier() {
            Assumptions.assumeTrue(supportsComponentFactories(),
                                   "Ignore test since ComponentFactories are not supported.");

            AtomicBoolean invoked = new AtomicBoolean(false);
            TestComponent expectedDefaultComponent = new TestComponent("default");
            String expectedFactoryState = "constructed";
            AtomicBoolean constructSwitch = new AtomicBoolean(true);
            TestComponentFactory testFactory = spy(new TestComponentFactory(expectedFactoryState, constructSwitch));

            testSubject.componentRegistry(registry -> registry.registerFactory(testFactory));

            AxonConfiguration config = buildConfiguration();

            TestComponent result = config.getComponent(TestComponent.class, "first", () -> {
                invoked.set(true);
                return expectedDefaultComponent;
            });
            assertFalse(invoked.get());
            assertNotEquals(expectedDefaultComponent, result);
            assertEquals(expectedFactoryState, result.state());
            // Turn the factory off. This should trigger the default supplier.
            constructSwitch.set(false);
            result = config.getComponent(TestComponent.class, "second", () -> {
                invoked.set(true);
                return expectedDefaultComponent;
            });
            assertTrue(invoked.get());
            assertEquals(expectedDefaultComponent, result);
            assertNotEquals(expectedFactoryState, result.state());
            verify(testFactory, times(2)).construct(any(), any());
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void startLifecycleHandlersAreInvokedInAscendingPhaseOrder() {
            Assumptions.assumeTrue(
                    doesOwnLifecycleManagement(),
                    "Ignore test since lifecycle management is not managed by the ApplicationConfigurer itself."
            );

            LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseTenHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseOverNineThousandHandler = spy(new LifecycleManagedInstance());

            testSubject.lifecycleRegistry(lc -> lc.onStart(0, phaseZeroHandler::start));
            testSubject.lifecycleRegistry(lc -> lc.onStart(1, phaseOneHandler::start));
            testSubject.lifecycleRegistry(lc -> lc.onStart(10, phaseTenHandler::start));
            testSubject.lifecycleRegistry(lc -> lc.onStart(9001, phaseOverNineThousandHandler::start));

            testSubject.start();

            InOrder lifecycleOrder = inOrder(phaseZeroHandler,
                                             phaseOneHandler,
                                             phaseTenHandler,
                                             phaseOverNineThousandHandler);
            lifecycleOrder.verify(phaseZeroHandler).start();
            lifecycleOrder.verify(phaseOneHandler).start();
            lifecycleOrder.verify(phaseTenHandler).start();
            lifecycleOrder.verify(phaseOverNineThousandHandler).start();
        }

        @Test
        void startLifecycleHandlerConfiguredThroughConfigurerAreInvokedInAscendingPhaseOrder() {
            Assumptions.assumeTrue(
                    doesOwnLifecycleManagement(),
                    "Ignore test since lifecycle management is not managed by the ApplicationConfigurer itself."
            );

            LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseTenHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseOverNineThousandHandler = spy(new LifecycleManagedInstance());

            testSubject.lifecycleRegistry(lc -> lc.onStart(9001, phaseOverNineThousandHandler::start));
            testSubject.lifecycleRegistry(lc -> lc.onStart(10, phaseTenHandler::start));
            testSubject.lifecycleRegistry(lc -> lc.onStart(1, phaseOneHandler::start));
            testSubject.lifecycleRegistry(lc -> lc.onStart(0, phaseZeroHandler::start));

            testSubject.start();

            InOrder lifecycleOrder = inOrder(phaseZeroHandler,
                                             phaseOneHandler,
                                             phaseTenHandler,
                                             phaseOverNineThousandHandler);
            lifecycleOrder.verify(phaseZeroHandler).start();
            lifecycleOrder.verify(phaseOneHandler).start();
            lifecycleOrder.verify(phaseTenHandler).start();
            lifecycleOrder.verify(phaseOverNineThousandHandler).start();
        }

        // Suppress Thread.sleep, as it's mandatory for this test.
        @Test
        @SuppressWarnings("java:S2925")
        void startLifecycleHandlersWillOnlyProceedToFollowingPhaseAfterCurrentPhaseIsFinalized()
                throws InterruptedException {
            Assumptions.assumeTrue(
                    doesOwnLifecycleManagement(),
                    "Ignore test since lifecycle management is not managed by the ApplicationConfigurer itself."
            );

            // Create a lock for the slow handler and lock it immediately, to spoof the handler's slow/long process
            ReentrantLock slowHandlerLock = new ReentrantLock();
            slowHandlerLock.lock();

            LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance slowPhaseZeroHandler = spy(new LifecycleManagedInstance(slowHandlerLock));
            LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());

            testSubject.lifecycleRegistry(lc -> lc.onStart(0, phaseZeroHandler::start));
            testSubject.lifecycleRegistry(lc -> lc.onStart(0, slowPhaseZeroHandler::slowStart));
            testSubject.lifecycleRegistry(lc -> lc.onStart(1, phaseOneHandler::start));

            // Start in a different thread as the 'slowPhaseZeroHandler' will otherwise not lock
            Thread startThread = new Thread(() -> testSubject.start());
            startThread.start();
            // Sleep to give the start thread some time to execute
            Thread.sleep(350);

            try {
                // Phase one has not started yet, as the method has not been invoked yet.
                verify(phaseOneHandler, never()).start();
                // The phase zero handlers on the other hand have been invoked
                verify(phaseZeroHandler).start();
                verify(slowPhaseZeroHandler).slowStart();
            } finally {
                slowHandlerLock.unlock();
            }

            // Wait until the start thread is finished prior to validating the order.
            startThread.join();

            verify(phaseOneHandler).start();

            InOrder lifecycleOrder = inOrder(phaseZeroHandler, slowPhaseZeroHandler, phaseOneHandler);
            lifecycleOrder.verify(phaseZeroHandler).start();
            lifecycleOrder.verify(slowPhaseZeroHandler).slowStart();
            lifecycleOrder.verify(phaseOneHandler).start();
        }

        @Test
        void shutdownLifecycleHandlersAreInvokedInDescendingPhaseOrder() {
            Assumptions.assumeTrue(
                    doesOwnLifecycleManagement(),
                    "Ignore test since lifecycle management is not managed by the ApplicationConfigurer itself."
            );

            LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseTenHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseOverNineThousandHandler = spy(new LifecycleManagedInstance());

            testSubject.lifecycleRegistry(lc -> lc.onShutdown(9001, phaseOverNineThousandHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(10, phaseTenHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(1, phaseOneHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(0, phaseZeroHandler::shutdown));

            AxonConfiguration rootConfig = testSubject.start();
            rootConfig.shutdown();

            InOrder lifecycleOrder = inOrder(phaseOverNineThousandHandler,
                                             phaseTenHandler,
                                             phaseOneHandler,
                                             phaseZeroHandler);
            lifecycleOrder.verify(phaseOverNineThousandHandler).shutdown();
            lifecycleOrder.verify(phaseTenHandler).shutdown();
            lifecycleOrder.verify(phaseOneHandler).shutdown();
            lifecycleOrder.verify(phaseZeroHandler).shutdown();
        }

        @Test
        void shutdownLifecycleHandlersConfiguredThroughConfigurerAreInvokedInDescendingPhaseOrder() {
            Assumptions.assumeTrue(
                    doesOwnLifecycleManagement(),
                    "Ignore test since lifecycle management is not managed by the ApplicationConfigurer itself."
            );

            LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseTenHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseOverNineThousandHandler = spy(new LifecycleManagedInstance());

            testSubject.lifecycleRegistry(lc -> lc.onShutdown(0, phaseZeroHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(1, phaseOneHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(10, phaseTenHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(9001, phaseOverNineThousandHandler::shutdown));

            AxonConfiguration rootConfig = testSubject.start();
            rootConfig.shutdown();

            InOrder lifecycleOrder = inOrder(phaseOverNineThousandHandler,
                                             phaseTenHandler,
                                             phaseOneHandler,
                                             phaseZeroHandler);
            lifecycleOrder.verify(phaseOverNineThousandHandler).shutdown();
            lifecycleOrder.verify(phaseTenHandler).shutdown();
            lifecycleOrder.verify(phaseOneHandler).shutdown();
            lifecycleOrder.verify(phaseZeroHandler).shutdown();
        }

        // Suppress Thread.sleep, as it's mandatory for this test.
        @Test
        @SuppressWarnings("java:S2925")
        void shutdownLifecycleHandlersWillOnlyProceedToFollowingPhaseAfterCurrentPhaseIsFinalized()
                throws InterruptedException {
            Assumptions.assumeTrue(
                    doesOwnLifecycleManagement(),
                    "Ignore test since lifecycle management is not managed by the ApplicationConfigurer itself."
            );

            // Create a lock for the slow handler and lock it immediately, to spoof the handler's slow/long process
            ReentrantLock slowHandlerLock = new ReentrantLock();
            slowHandlerLock.lock();

            LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance slowPhaseOneHandler = spy(new LifecycleManagedInstance(slowHandlerLock));
            LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());

            testSubject.lifecycleRegistry(lc -> lc.onShutdown(1, phaseOneHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(1, slowPhaseOneHandler::slowShutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(0, phaseZeroHandler::shutdown));

            AxonConfiguration rootConfig = testSubject.start();

            // Start in a different thread as the 'slowPhaseOneHandler' will otherwise not lock
            Thread shutdownThread = new Thread(rootConfig::shutdown);
            shutdownThread.start();
            // Sleep to give the shutdown thread some time to execute
            Thread.sleep(250);

            try {
                // Phase one has not started yet, as the method has not been invoked yet.
                verify(phaseZeroHandler, never()).shutdown();
                // The phase zero handlers on the other hand have been invoked
                verify(phaseOneHandler).shutdown();
                verify(slowPhaseOneHandler).slowShutdown();
            } finally {
                slowHandlerLock.unlock();
            }

            // Wait until the shutdown-thread is finished prior to validating the order.
            shutdownThread.join();
            verify(phaseZeroHandler).shutdown();

            InOrder lifecycleOrder = inOrder(phaseOneHandler, slowPhaseOneHandler, phaseZeroHandler);
            lifecycleOrder.verify(phaseOneHandler).shutdown();
            lifecycleOrder.verify(slowPhaseOneHandler).slowShutdown();
            lifecycleOrder.verify(phaseZeroHandler).shutdown();
        }

        /**
         * To be honest, I don't know why somebody would add a start handler during shutdown, but since the validation
         * is there through the lifecycle state I wanted to test it regardless.
         */
        @Test
        void outOfOrderAddedStartHandlerDuringShutdownIsNotCalledImmediately() {
            Assumptions.assumeTrue(
                    doesOwnLifecycleManagement(),
                    "Ignore test since lifecycle management is not managed by the ApplicationConfigurer itself."
            );

            LifecycleManagedInstance phaseTwoHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseOneHandlerAdder = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance addedPhaseOneStartHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());

            testSubject.lifecycleRegistry(lc -> lc.onShutdown(2, phaseTwoHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(1,
                                                              () -> phaseOneHandlerAdder.addLifecycleHandler(
                                                                      LifecycleRegistry::onStart,
                                                                      lc,
                                                                      1,
                                                                      addedPhaseOneStartHandler::start)));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(1, phaseOneHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(0, phaseZeroHandler::shutdown));

            AxonConfiguration rootConfig = testSubject.start();
            rootConfig.shutdown();

            InOrder lifecycleOrder = inOrder(phaseTwoHandler, phaseOneHandlerAdder, phaseOneHandler, phaseZeroHandler);
            lifecycleOrder.verify(phaseTwoHandler).shutdown();
            lifecycleOrder.verify(phaseOneHandlerAdder).addLifecycleHandler(any(), any(), eq(1), any());
            lifecycleOrder.verify(phaseOneHandler).shutdown();
            lifecycleOrder.verify(phaseZeroHandler).shutdown();

            verifyNoInteractions(addedPhaseOneStartHandler);
        }

        @Test
        void failingStartLifecycleProceedsIntoShutdownOrderAtFailingPhase() {
            Assumptions.assumeTrue(
                    doesOwnLifecycleManagement(),
                    "Ignore test since lifecycle management is not managed by the ApplicationConfigurer itself."
            );

            LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseTwoHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseThreeHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseFourHandler = spy(new LifecycleManagedInstance());

            testSubject.lifecycleRegistry(lc -> lc.onStart(0, phaseZeroHandler::start));
            testSubject.lifecycleRegistry(lc -> lc.onStart(1, phaseOneHandler::start));
            // The LifecycleManagedInstance#failingStart() should trigger a shutdown as of phase 2
            testSubject.lifecycleRegistry(lc -> lc.onStart(2, phaseTwoHandler::failingStart));
            testSubject.lifecycleRegistry(lc -> lc.onStart(3, phaseThreeHandler::start));
            testSubject.lifecycleRegistry(lc -> lc.onStart(4, phaseFourHandler::start));

            testSubject.lifecycleRegistry(lc -> lc.onShutdown(4, phaseFourHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(3, phaseThreeHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(2, phaseTwoHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(1, phaseOneHandler::shutdown));
            testSubject.lifecycleRegistry(lc -> lc.onShutdown(0, phaseZeroHandler::shutdown));

            try {
                testSubject.start();
                fail("Expected a LifecycleHandlerInvocationException to be thrown");
            } catch (LifecycleHandlerInvocationException e) {
                assertTrue(e.getCause().getMessage().contains(START_FAILURE_EXCEPTION_MESSAGE));
            }

            InOrder lifecycleOrder = inOrder(phaseZeroHandler,
                                             phaseOneHandler,
                                             phaseTwoHandler,
                                             phaseThreeHandler,
                                             phaseFourHandler);
            lifecycleOrder.verify(phaseZeroHandler).start();
            lifecycleOrder.verify(phaseOneHandler).start();
            lifecycleOrder.verify(phaseTwoHandler).failingStart();
            lifecycleOrder.verify(phaseFourHandler).shutdown();
            lifecycleOrder.verify(phaseThreeHandler).shutdown();
            lifecycleOrder.verify(phaseTwoHandler).shutdown();
            lifecycleOrder.verify(phaseOneHandler).shutdown();
            lifecycleOrder.verify(phaseZeroHandler).shutdown();
        }

        @Test
        void lifecycleHandlersProceedToFollowingPhaseWhenTheThreadIsInterrupted() throws InterruptedException {
            Assumptions.assumeTrue(
                    doesOwnLifecycleManagement(),
                    "Ignore test since lifecycle management is not managed by the ApplicationConfigurer itself."
            );

            AtomicBoolean invoked = new AtomicBoolean(false);

            LifecycleManagedInstance phaseZeroHandler = spy(new LifecycleManagedInstance());
            LifecycleManagedInstance phaseOneHandler = spy(new LifecycleManagedInstance(invoked));
            LifecycleManagedInstance phaseTwoHandler = spy(new LifecycleManagedInstance());

            testSubject.lifecycleRegistry(lc -> lc.onStart(0, phaseZeroHandler::start));
            testSubject.lifecycleRegistry(lc -> lc.onStart(1, phaseOneHandler::uncompletableStart));
            testSubject.lifecycleRegistry(lc -> lc.onStart(2, phaseTwoHandler::start));

            // Start in a different thread to be able to interrupt the thread
            Thread startThread = new Thread(testSubject::start);
            startThread.start();
            startThread.interrupt();

            // Wait until the start thread is finished prior to validating the order.
            startThread.join();

            InOrder lifecycleOrder = inOrder(phaseZeroHandler, phaseOneHandler, phaseTwoHandler);
            lifecycleOrder.verify(phaseZeroHandler).start();
            lifecycleOrder.verify(phaseOneHandler).uncompletableStart();
            lifecycleOrder.verify(phaseTwoHandler).start();
            assertFalse(invoked.get());
        }

        // Suppress Thread.sleep, as it's mandatory for this test.
        @Test
        @SuppressWarnings("java:S2925")
        void timeOutContinuesWithTheNextLifecyclePhase() throws InterruptedException {
            Assumptions.assumeTrue(
                    doesOwnLifecycleManagement(),
                    "Ignore test since lifecycle management is not managed by the ApplicationConfigurer itself."
            );

            CountDownLatch handlerStarted = new CountDownLatch(1);
            AtomicBoolean handler1Invoked = new AtomicBoolean();
            AtomicBoolean handler2Invoked = new AtomicBoolean();
            testSubject.lifecycleRegistry(r -> r
                    .onStart(1, () -> {
                        handlerStarted.countDown();
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    })
                    .onStart(1, () -> handler1Invoked.set(true))
                    .onStart(2, () -> handler2Invoked.set(true))
                    .registerLifecyclePhaseTimeout(10, TimeUnit.MILLISECONDS));

            Thread t = new Thread(testSubject::start);
            t.start();
            assertTrue(handlerStarted.await(1, TimeUnit.SECONDS), "Handler didn't start in a second.");

            assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(handler2Invoked.get()));
            assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(handler1Invoked.get()));

            t.join();
        }

        @Test
        void factoryRegisteredShutdownHandlersAreInvoked() {
            Assumptions.assumeTrue(
                    doesOwnLifecycleManagement(),
                    "Ignore test since lifecycle management is not managed by the ApplicationConfigurer itself."
            );

            // given...
            AtomicBoolean shutdownInvoked = new AtomicBoolean(false);
            testSubject.componentRegistry(registry -> registry.registerFactory(new TestComponentFactory(
                    "constructed", new AtomicBoolean(true), shutdownInvoked
            )));
            AxonConfiguration config = testSubject.start();
            // when...
            config.shutdown();
            // then...
            await("Await until factory shutdown handler was invoked").pollDelay(Duration.ofMillis(50))
                                                                     .atMost(Duration.ofSeconds(5))
                                                                     .until(shutdownInvoked::get);
        }

        @FunctionalInterface
        protected interface LifecycleRegistration {

            void registerLifecycleHandler(LifecycleRegistry lifecycleRegistry, int phase, Runnable lifecycleHandler);
        }

        static class LifecycleManagedInstance {

            private final ReentrantLock lock;
            private final AtomicBoolean invoked;

            protected LifecycleManagedInstance() {
                this(new ReentrantLock(), new AtomicBoolean(false));
            }

            protected LifecycleManagedInstance(ReentrantLock lock) {
                this(lock, new AtomicBoolean(false));
            }

            protected LifecycleManagedInstance(AtomicBoolean invoked) {
                this(new ReentrantLock(), invoked);
            }

            protected LifecycleManagedInstance(ReentrantLock lock, AtomicBoolean invoked) {
                this.lock = lock;
                this.invoked = invoked;
            }

            public void start() {
                // No-op
            }

            protected CompletableFuture<Void> slowStart() {
                return CompletableFuture.runAsync(() -> {
                    try {
                        LoggerFactory.getLogger(ApplicationConfigurerTestSuite.class)
                                     .info("Attempting to acquire lock");
                        lock.lock();
                    } finally {
                        LoggerFactory.getLogger(ApplicationConfigurerTestSuite.class).info("Lock acquired, unlocking");
                        lock.unlock();
                    }
                });
            }

            protected CompletableFuture<Object> uncompletableStart() {
                return new CompletableFuture<>().whenComplete((r, e) -> invoked.set(true));
            }

            protected void addLifecycleHandler(LifecycleRegistration lifecycleRegistration,
                                               LifecycleRegistry lifecycleRegistry, int phase,
                                               Runnable lifecycleHandler) {
                lifecycleRegistration.registerLifecycleHandler(lifecycleRegistry, phase, lifecycleHandler);
            }

            protected void shutdown() {
                // No-op
            }

            protected CompletableFuture<Void> slowShutdown() {
                return CompletableFuture.runAsync(() -> {
                    try {
                        lock.lock();
                    } finally {
                        lock.unlock();
                    }
                });
            }

            protected void failingStart() {
                throw new RuntimeException(START_FAILURE_EXCEPTION_MESSAGE);
            }
        }
    }
}