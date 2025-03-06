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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.*;
import org.mockito.*;

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

    /**
     * Returns type {@code D} that this {@link #testSubject()} can delegate operations to.
     * <p>
     * Returns {@code null} when the implementation does not have a delegate.
     *
     * @param <D> A {@link NewConfigurer} type that this {@link #testSubject()} can delegate operations to.
     * @return The type {@code D} this {@link #testSubject()} can delegate operations to. Returns {@code null} when the
     * implementation does not have a delegate.
     */
    @Nullable
    public abstract <D extends NewConfigurer<D>> Class<D> delegateType();

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
        void registerComponentExposesRegisteredComponentOnOptionalGet() {
            TestComponent testComponent = TEST_COMPONENT;
            testSubject.registerComponent(TestComponent.class, c -> testComponent);
            NewConfiguration config = testSubject.build();

            Optional<TestComponent> result = config.getOptionalComponent(TestComponent.class);

            assertTrue(result.isPresent());
            assertEquals(testComponent, result.get());
        }

        @Test
        void getOptionalComponentResultsInEmptyOptionalForUnregisteredComponent() {
            Optional<TestComponent> result = testSubject.build()
                                                        .getOptionalComponent(TestComponent.class);

            assertFalse(result.isPresent());
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

        @Test
        void getComponentWithDefaultInvokesSupplierWhenThereIsNoRegisteredComponentForTheGivenClass() {
            AtomicBoolean invoked = new AtomicBoolean(false);
            TestComponent defaultComponent = new TestComponent("default");
            TestComponent registeredComponent = TEST_COMPONENT;

            NewConfiguration config = testSubject.registerComponent(TestComponent.class, "id", c -> registeredComponent)
                                                 .build();

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
    class HasComponent {

        @Test
        void hasComponentForClass() {
            assertFalse(testSubject.hasComponent(TestComponent.class));

            testSubject.registerComponent(TestComponent.class, c -> TEST_COMPONENT);

            assertTrue(testSubject.hasComponent(TestComponent.class));
        }

        @Test
        void hasComponentForClassAndName() {
            assertFalse(testSubject.hasComponent(TestComponent.class, "some-name"));

            testSubject.registerComponent(TestComponent.class, "some-name", c -> TEST_COMPONENT);

            assertTrue(testSubject.hasComponent(TestComponent.class, "some-name"));
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
        void registerDecoratorThrowsIllegalArgumentExceptionForNonExistingComponentType() {
            assertThrows(IllegalArgumentException.class,
                         () -> testSubject.registerDecorator(TestComponent.class, 42, (c, delegate) -> delegate));
        }

        @Test
        void registerDecoratorThrowsNullPointerExceptionForNullType() {
            testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT);

            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.registerDecorator(null, 42, (c, delegate) -> delegate));
        }

        @Test
        void registerDecoratorThrowsNullPointerExceptionForNullName() {
            testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT);

            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.registerDecorator(Object.class, null, 42, (c, delegate) -> delegate));
        }

        @Test
        void registerDecoratorThrowsNullPointerExceptionForNullComponentDecorator() {
            testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT);

            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> testSubject.registerDecorator(TestComponent.class, 42, null));
        }
    }

    @Nested
    class EnhancerRegistration {

        @Test
        void registerEnhancerThrowsNullPointerExceptionForNullEnhancer() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class, () -> testSubject.registerEnhancer(null));
        }

        @Test
        void registeredEnhancersAreInvokedDuringBuild() {
            AtomicBoolean invoked = new AtomicBoolean(false);

            testSubject.registerEnhancer(configurer -> invoked.set(true));

            testSubject.build();

            assertTrue(invoked.get());
        }

        @Test
        void registeredEnhancersAreInvokedDuringBuildOnlyOnce() {
            AtomicInteger counter = new AtomicInteger(0);

            testSubject.registerEnhancer(configurer -> counter.getAndIncrement());
            // First build
            testSubject.build();
            // Second build
            testSubject.build();

            assertEquals(1, counter.get());
        }

        @Test
        void registeredEnhancersAreInvokedBasedOnInsertOrder() {
            //noinspection Convert2Lambda - Cannot be lambda, as spying doesn't work otherwise.
            ConfigurerEnhancer enhancerOne = spy(new ConfigurerEnhancer() {

                @Override
                public void enhance(@Nonnull NewConfigurer<?> configurer) {
                    // Not important, so do nothing.
                }
            });
            //noinspection Convert2Lambda - Cannot be lambda, as spying doesn't work otherwise.
            ConfigurerEnhancer enhancerTwo = spy(new ConfigurerEnhancer() {
                @Override
                public void enhance(@Nonnull NewConfigurer<?> configurer) {
                    // Not important, so do nothing.
                }
            });
            //noinspection Convert2Lambda - Cannot be lambda, as spying doesn't work otherwise.
            ConfigurerEnhancer enhancerThree = spy(new ConfigurerEnhancer() {

                @Override
                public void enhance(@Nonnull NewConfigurer<?> configurer) {
                    // Not important, so do nothing.
                }
            });

            testSubject.registerEnhancer(enhancerOne)
                       .registerEnhancer(enhancerTwo)
                       .registerEnhancer(enhancerThree)
                       .build();

            InOrder enhancementOrder = inOrder(enhancerOne, enhancerTwo, enhancerThree);
            enhancementOrder.verify(enhancerOne).enhance(any());
            enhancementOrder.verify(enhancerTwo).enhance(any());
            enhancementOrder.verify(enhancerThree).enhance(any());
        }

        @Test
        void registeredEnhancersAreInvokedBasedOnDefinedOrder() {
            ConfigurerEnhancer enhancerWithLowOrder = spy(new ConfigurerEnhancer() {

                @Override
                public void enhance(@Nonnull NewConfigurer<?> configurer) {
                    // Not important, so do nothing.
                }

                @Override
                public int order() {
                    return -42;
                }
            });

            //noinspection Convert2Lambda - Cannot be lambda, as spying doesn't work otherwise.
            ConfigurerEnhancer enhancerWithDefaultOrder = spy(new ConfigurerEnhancer() {
                @Override
                public void enhance(@Nonnull NewConfigurer<?> configurer) {
                    // Not important, so do nothing.
                }
            });

            ConfigurerEnhancer enhancerWithHighOrder = spy(new ConfigurerEnhancer() {

                @Override
                public void enhance(@Nonnull NewConfigurer<?> configurer) {
                    // Not important, so do nothing.
                }

                @Override
                public int order() {
                    return 42;
                }
            });

            testSubject.registerEnhancer(enhancerWithDefaultOrder)
                       .registerEnhancer(enhancerWithHighOrder)
                       .registerEnhancer(enhancerWithLowOrder)
                       .build();

            InOrder enhancementOrder = inOrder(enhancerWithLowOrder, enhancerWithDefaultOrder, enhancerWithHighOrder);
            enhancementOrder.verify(enhancerWithLowOrder).enhance(any());
            enhancementOrder.verify(enhancerWithDefaultOrder).enhance(any());
            enhancementOrder.verify(enhancerWithHighOrder).enhance(any());
        }

        @Test
        void registeredEnhancersCanAddComponents() {
            testSubject.registerEnhancer(
                    configurer -> configurer.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
            );

            NewConfiguration config = testSubject.build();

            assertEquals(TEST_COMPONENT, config.getComponent(TestComponent.class));
        }

        @Test
        void registeredEnhancersCanDecorateComponents() {
            TestComponent expected = new TestComponent(TEST_COMPONENT.state() + "-decorated");
            ConfigurerEnhancer enhancer = configurer -> configurer.registerDecorator(
                    TestComponent.class, 0, (c, delegate) -> new TestComponent(delegate.state() + "-decorated")
            );
            testSubject.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                       .registerEnhancer(enhancer);

            NewConfiguration config = testSubject.build();

            assertEquals(expected, config.getComponent(TestComponent.class));
        }

        @Test
        void registeredEnhancersCanReplaceComponents() {
            TestComponent expected = new TestComponent("replacement");

            testSubject.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                       .registerEnhancer(configurer -> configurer.registerComponent(TestComponent.class,
                                                                                    c -> expected));

            NewConfiguration config = testSubject.build();

            assertNotEquals(TEST_COMPONENT, config.getComponent(TestComponent.class));
            assertEquals(expected, config.getComponent(TestComponent.class));
        }

        @Test
        void registeredEnhancersCanReplaceComponentsConditionally() {
            TestComponent expected = new TestComponent("conditional");

            testSubject.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                       .registerEnhancer(configurer -> {
                           if (configurer.hasComponent(TestComponent.class)) {
                               configurer.registerComponent(TestComponent.class, "conditional", c -> expected);
                           }
                       });

            NewConfiguration config = testSubject.build();

            assertEquals(TEST_COMPONENT, config.getComponent(TestComponent.class));
            assertEquals(expected, config.getComponent(TestComponent.class, "conditional"));
        }
    }

    @Nested
    class ModuleRegistration {

        @Test
        void registerModuleThrowsNullPointerExceptionForNullModuleBuilder() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class, () -> testSubject.registerModule(null));
        }

        @Test
        void registerModuleExposesModulesConfigurationsUponBuild() {
            testSubject.registerModule(TestModule::new)
                       .registerModule(TestModule::new);

            List<NewConfiguration> result = testSubject.build().getModuleConfigurations();

            assertEquals(2, result.size());
        }

        @Test
        void canRetrieveComponentsFromModuleAndParentOnly() {
            TestComponent rootComponent = new TestComponent("root");
            TestComponent levelOneModuleComponent = new TestComponent("root-one");
            TestComponent levelTwoModuleComponent = new TestComponent("root-two");

            testSubject.registerComponent(TestComponent.class, "root", rootConfig -> rootComponent)
                       .registerModule(
                               rootConfig -> new TestModule(rootConfig)
                                       .registerComponent(
                                               TestComponent.class, "one",
                                               c -> c.getOptionalComponent(TestComponent.class, "root")
                                                     .map(delegate -> new TestComponent(delegate.state + "-one"))
                                                     .orElseThrow()
                                       )
                                       .registerModule(
                                               moduleConfig -> new TestModule(moduleConfig)
                                                       .registerComponent(
                                                               TestComponent.class, "two",
                                                               c -> c.getOptionalComponent(TestComponent.class, "root")
                                                                     .map(delegate -> new TestComponent(
                                                                             delegate.state + "-two"
                                                                     ))
                                                                     .orElseThrow()
                                                       )
                                       )
                       );

            // Root configurer outcome only has own components.
            NewConfiguration rootConfig = testSubject.build();
            assertEquals(rootComponent, rootConfig.getComponent(TestComponent.class, "root"));
            assertFalse(rootConfig.getOptionalComponent(TestComponent.class, "one").isPresent());
            assertFalse(rootConfig.getOptionalComponent(TestComponent.class, "two").isPresent());
            // Level one module outcome has own components and access to parent.
            List<NewConfiguration> levelOneConfigurations = rootConfig.getModuleConfigurations();
            assertEquals(1, levelOneConfigurations.size());
            NewConfiguration levelOneConfig = levelOneConfigurations.getFirst();
            assertTrue(levelOneConfig.getOptionalComponent(TestComponent.class, "root").isPresent());
            assertEquals(levelOneModuleComponent, levelOneConfig.getComponent(TestComponent.class, "one"));
            assertFalse(levelOneConfig.getOptionalComponent(TestComponent.class, "two").isPresent());
            // Level two module outcome has own components and access to parent, and parent's parent.
            List<NewConfiguration> levelTwoConfigurations = levelOneConfig.getModuleConfigurations();
            assertEquals(1, levelTwoConfigurations.size());
            NewConfiguration levelTwoConfig = levelTwoConfigurations.getFirst();
            assertTrue(levelTwoConfig.getOptionalComponent(TestComponent.class, "root").isPresent());
            assertTrue(levelTwoConfig.getOptionalComponent(TestComponent.class, "one").isPresent());
            assertEquals(levelTwoModuleComponent, levelTwoConfig.getComponent(TestComponent.class, "two"));
        }

        @Test
        void cannotRetrieveComponentsRegisteredFromModulesRegisteredOnTheSameLevel() {
            TestComponent rootComponent = new TestComponent("root");
            TestComponent leftModuleComponent = new TestComponent("left");
            TestComponent rightModuleComponent = new TestComponent("right");
            testSubject.registerComponent(TestComponent.class, "root", rootConfig -> rootComponent)
                       .registerModule(
                               rootConfig -> new TestModule(rootConfig)
                                       .registerComponent(TestComponent.class, "left", c -> leftModuleComponent)
                       )
                       .registerModule(
                               rootConfig -> new TestModule(rootConfig)
                                       .registerComponent(TestComponent.class, "right", c -> rightModuleComponent)
                       );

            // Root configurer outcome only has own components.
            NewConfiguration rootConfig = testSubject.build();
            assertEquals(rootComponent, rootConfig.getComponent(TestComponent.class, "root"));
            assertFalse(rootConfig.getOptionalComponent(TestComponent.class, "one").isPresent());
            assertFalse(rootConfig.getOptionalComponent(TestComponent.class, "two").isPresent());
            List<NewConfiguration> levelOneConfigurations = rootConfig.getModuleConfigurations();
            assertEquals(2, levelOneConfigurations.size());
            // Left module can access own components and parent, not its siblings.
            NewConfiguration leftConfig = levelOneConfigurations.getFirst();
            assertTrue(leftConfig.getOptionalComponent(TestComponent.class, "root").isPresent());
            assertEquals(leftModuleComponent, leftConfig.getComponent(TestComponent.class, "left"));
            assertFalse(leftConfig.getOptionalComponent(TestComponent.class, "right").isPresent());
            // Right module can access own components and parent, not its siblings,
            NewConfiguration rightConfig = levelOneConfigurations.get(1);
            assertTrue(rightConfig.getOptionalComponent(TestComponent.class, "root").isPresent());
            assertEquals(rightModuleComponent, rightConfig.getComponent(TestComponent.class, "right"));
            assertFalse(rightConfig.getOptionalComponent(TestComponent.class, "left").isPresent());
        }

        @Test
        void decoratingOnlyOccursOnTheModuleLevelItIsInvokedOn() {
            String expectedRootComponentState = "root-decorated-by-root";
            String expectedLevelOneComponentState = "level-one-decorated-by-level-one";
            String expectedLevelTwoComponentState = "level-two-decorated-by-level-two";
            testSubject.registerComponent(
                               TestComponent.class, rootConfig -> new TestComponent("root")
                       )
                       .registerDecorator(
                               TestComponent.class, 0,
                               (rootConfig, delegate) -> new TestComponent(
                                       delegate.state() + "-decorated-by-root"
                               )
                       )
                       .registerModule(
                               rootConfig -> new TestModule(rootConfig)
                                       .registerComponent(
                                               TestComponent.class,
                                               c -> new TestComponent("level-one")
                                       )
                                       .registerDecorator(
                                               TestComponent.class, 0,
                                               (config, delegate) -> new TestComponent(
                                                       delegate.state() + "-decorated-by-level-one"
                                               )
                                       )
                                       .registerModule(
                                               moduleConfig -> new TestModule(moduleConfig)
                                                       .registerComponent(
                                                               TestComponent.class,
                                                               config -> new TestComponent("level-two")
                                                       )
                                                       .registerDecorator(
                                                               TestComponent.class, 0,
                                                               (config, delegate) -> new TestComponent(
                                                                       delegate.state() + "-decorated-by-level-two"
                                                               )
                                                       )
                                       )
                       );

            // Check decoration on root level.
            NewConfiguration root = testSubject.build();
            assertEquals(expectedRootComponentState, root.getComponent(TestComponent.class).state());
            assertNotEquals(expectedLevelOneComponentState, root.getComponent(TestComponent.class).state());
            assertNotEquals(expectedLevelTwoComponentState, root.getComponent(TestComponent.class).state());
            // Check decoration on level one.
            List<NewConfiguration> rootModuleConfigs = root.getModuleConfigurations();
            assertEquals(1, rootModuleConfigs.size());
            NewConfiguration levelOne = rootModuleConfigs.getFirst();
            assertNotEquals(expectedRootComponentState, levelOne.getComponent(TestComponent.class).state());
            assertEquals(expectedLevelOneComponentState, levelOne.getComponent(TestComponent.class).state());
            assertNotEquals(expectedLevelTwoComponentState, levelOne.getComponent(TestComponent.class).state());
            // Check decoration on level two.
            List<NewConfiguration> levelOneConfigs = levelOne.getModuleConfigurations();
            assertEquals(1, levelOneConfigs.size());
            NewConfiguration levelTwo = levelOneConfigs.getFirst();
            assertNotEquals(expectedRootComponentState, levelTwo.getComponent(TestComponent.class).state());
            assertNotEquals(expectedLevelOneComponentState, levelTwo.getComponent(TestComponent.class).state());
            assertEquals(expectedLevelTwoComponentState, levelTwo.getComponent(TestComponent.class).state());
        }

        @Test
        void getComponentWithDefaultChecksCurrentModuleAndParent() {
            AtomicBoolean invoked = new AtomicBoolean(false);
            TestComponent defaultComponent = new TestComponent("default");
            TestComponent registeredComponent = TEST_COMPONENT;

            NewConfiguration rootConfig =
                    testSubject.registerModule(
                                       config -> new TestModule(config)
                                               .registerComponent(
                                                       TestComponent.class, "id",
                                                       c -> registeredComponent
                                               )
                               )
                               .build();

            TestComponent result = rootConfig.getComponent(TestComponent.class, "id", () -> {
                invoked.set(true);
                return defaultComponent;
            });

            assertTrue(invoked.get());
            assertEquals(defaultComponent, result);
            assertNotEquals(registeredComponent, result);

            invoked.set(false);
            List<NewConfiguration> levelOneConfigs = rootConfig.getModuleConfigurations();
            assertEquals(1, levelOneConfigs.size());
            NewConfiguration levelOneConfig = levelOneConfigs.getFirst();
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
    class ConfigurationDelegation {

        @Test
        void doesNotInvokeConfigureTaskWhenThisConfigurerDoesNotDelegate() {
            if (delegateType() != null) {
                Assumptions.abort("Cannot validate this scenario since the test subject has configurer delegate.");
            }

            AtomicBoolean invoked = new AtomicBoolean(false);

            //noinspection unchecked
            testSubject.delegate(NewConfigurer.class, config -> invoked.set(true));

            assertFalse(invoked.get());
        }

        @Test
        void doesNotInvokeConfigureTaskWhenTheDelegateIsNotOfTheExpectedType() {
            if (delegateType() == null) {
                Assumptions.abort("Cannot validate this scenario since the test subject has no configurer delegate.");
            }

            AtomicBoolean invoked = new AtomicBoolean(false);

            testSubject.delegate(TestModule.class, config -> invoked.set(true));

            assertFalse(invoked.get());
        }

        @Test
        void invokeConfigureTaskWhenTestSubjectHasDelegateConfigurerAndTypeMatches() {
            //noinspection rawtypes,unchecked
            Class<NewConfigurer> delegateType = delegateType();
            if (delegateType == null) {
                Assumptions.abort("Cannot validate this scenario since the test subject has no configurer delegate.");
            }

            AtomicBoolean invoked = new AtomicBoolean(false);

            //noinspection unchecked
            testSubject.delegate(delegateType, config -> invoked.set(true));

            assertTrue(invoked.get());
        }
    }

    protected record TestComponent(String state) {

    }

    protected static class TestModule extends AbstractConfigurer<TestModule> implements Module<TestModule> {

        protected TestModule(@Nullable LifecycleSupportingConfiguration config) {
            super(config);
        }
    }
}