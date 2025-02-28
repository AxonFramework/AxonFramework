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
    class ModuleRegistration {

        @Test
        void registerModuleThrowsNullPointerExceptionForNullModuleBuilder() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class, () -> testSubject.registerModule(null));
        }

        @Test
        void registerModuleExposesRegisteredModuleThroughGetModulesForUponBuild() {
            AtomicReference<TestModule> moduleOneReference = new AtomicReference<>();
            AtomicReference<TestModule> moduleTwoReference = new AtomicReference<>();

            testSubject.registerModule(config -> {
                           TestModule moduleOne = new TestModule(config);
                           moduleOneReference.set(moduleOne);
                           return moduleOne;
                       })
                       .registerModule(config -> {
                           TestModule moduleTwo = new TestModule(config);
                           moduleTwoReference.set(moduleTwo);
                           return moduleTwo;
                       });

            List<TestModule> result = testSubject.build().getModulesFor(TestModule.class);

            assertEquals(2, result.size());
            assertTrue(result.contains(moduleOneReference.get()));
            assertTrue(result.contains(moduleTwoReference.get()));
        }

        @Test
        void registerModuleExposesRegisteredModuleThroughModulesUponBuild() {
            AtomicReference<TestModule> moduleOneReference = new AtomicReference<>();
            AtomicReference<TestModule> moduleTwoReference = new AtomicReference<>();

            testSubject.registerModule(config -> {
                           TestModule moduleOne = new TestModule(config);
                           moduleOneReference.set(moduleOne);
                           return moduleOne;
                       })
                       .registerModule(config -> {
                           TestModule moduleTwo = new TestModule(config);
                           moduleTwoReference.set(moduleTwo);
                           return moduleTwo;
                       });

            List<Module<?>> result = testSubject.build().getModules();

            assertEquals(2, result.size());
            assertTrue(result.contains(moduleOneReference.get()));
            assertTrue(result.contains(moduleTwoReference.get()));
        }

        @Test
        void canRetrieveComponentsRegisteredOnSubModule() {
            TestComponent rootComponent = new TestComponent("root");
            TestComponent levelOneModuleComponent = new TestComponent("level-one");
            TestComponent levelTwoModuleComponent = new TestComponent("level-two");
            testSubject.registerComponent(TestComponent.class, "root", rootConfig -> rootComponent)
                       .registerModule(
                               rootConfig -> new TestModule(rootConfig)
                                       .registerComponent(TestComponent.class, "one", c -> levelOneModuleComponent)
                                       .registerModule(c -> new TestModule(c)
                                               .registerComponent(TestComponent.class, "two",
                                                                  config -> levelTwoModuleComponent)
                                       )
                       );

            NewConfiguration result = testSubject.build();

            assertEquals(rootComponent, result.getComponent(TestComponent.class, "root"));
            assertEquals(levelOneModuleComponent, result.getComponent(TestComponent.class, "one"));
            assertEquals(levelTwoModuleComponent, result.getComponent(TestComponent.class, "two"));
        }

        @Test
        void registeredModulesExposeModulesToParentConfigurer() {
            AtomicReference<TestModule> rootModuleReference = new AtomicReference<>();
            AtomicReference<TestModule> levelOneModuleReference = new AtomicReference<>();
            AtomicReference<TestModule> levelTwoModuleReference = new AtomicReference<>();
            AtomicReference<TestModule> levelThreeModuleReference = new AtomicReference<>();

            testSubject.registerModule(rootConfig -> {
                // Root level
                TestModule rootModule = new TestModule(rootConfig)
                        .registerModule(moduleOneConfig -> {
                            // Level one
                            TestModule levelOneModule = new TestModule(moduleOneConfig)
                                    .registerModule(moduleTwoConfig -> {
                                        // Level two
                                        TestModule levelTwoModule = new TestModule(moduleTwoConfig)
                                                .registerModule(moduleThreeConfig -> {
                                                    // Level three
                                                    TestModule levelThreeModule = new TestModule(moduleThreeConfig);
                                                    levelThreeModuleReference.set(levelThreeModule);
                                                    return levelThreeModule;
                                                });
                                        levelTwoModuleReference.set(levelTwoModule);
                                        return levelTwoModule;
                                    });
                            levelOneModuleReference.set(levelOneModule);
                            return levelOneModule;
                        });
                rootModuleReference.set(rootModule);
                return rootModule;
            });

            List<Module<?>> result = testSubject.build().getModules();

            assertEquals(4, result.size());
            assertTrue(result.contains(rootModuleReference.get()));
            assertTrue(result.contains(levelOneModuleReference.get()));
            assertTrue(result.contains(levelTwoModuleReference.get()));
            assertTrue(result.contains(levelThreeModuleReference.get()));
        }

        @Test
        void decoratingOnlyOccursOnTheModuleLevelItIsInvokedOn() {
            String expectedRootComponentState = "root-decorated-by-root";
            String expectedLevelOneComponentState = "level-one-decorated-by-level-one";
            String expectedLevelTwoComponentState = "level-two-decorated-by-level-two";
            testSubject.registerComponent(
                               TestComponent.class, "root", rootConfig -> new TestComponent("root")
                       )
                       .registerDecorator(
                               TestComponent.class, "root", 0,
                               (rootConfig, delegate) -> new TestComponent(
                                       delegate.state() + "-decorated-by-root"
                               )
                       )
                       .registerModule(
                               rootConfig -> new TestModule(rootConfig)
                                       .registerComponent(
                                               TestComponent.class, "one",
                                               c -> new TestComponent("level-one")
                                       )
                                       .registerDecorator(
                                               TestComponent.class, "one", 0,
                                               (config, delegate) -> new TestComponent(
                                                       delegate.state() + "-decorated-by-level-one"
                                               )
                                       )
                                       .registerModule(
                                               moduleConfig -> new TestModule(moduleConfig)
                                                       .registerComponent(
                                                               TestComponent.class, "two",
                                                               config -> new TestComponent("level-two")
                                                       )
                                                       .registerDecorator(
                                                               TestComponent.class, "two", 0,
                                                               (config, delegate) -> new TestComponent(
                                                                       delegate.state() + "-decorated-by-level-two"
                                                               )
                                                       )
                                       )
                       );

            NewConfiguration result = testSubject.build();

            assertEquals(expectedRootComponentState, result.getComponent(TestComponent.class, "root").state());
            assertEquals(expectedLevelOneComponentState, result.getComponent(TestComponent.class, "one").state());
            assertEquals(expectedLevelTwoComponentState, result.getComponent(TestComponent.class, "two").state());
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