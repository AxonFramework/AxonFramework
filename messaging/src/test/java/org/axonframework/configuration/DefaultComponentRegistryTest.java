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
import org.axonframework.utils.StubLifecycleRegistry;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DefaultComponentRegistry}.
 *
 * @author Allard Buijze
 */
class DefaultComponentRegistryTest {

    private static final String INIT_STATE = "initial-state";
    private static final TestComponent TEST_COMPONENT = new TestComponent(INIT_STATE);

    private DefaultComponentRegistry testSubject;
    private LifecycleRegistry lifecycleRegistry;

    @BeforeEach
    void setUp() {
        testSubject = new DefaultComponentRegistry();
        lifecycleRegistry = mock(LifecycleRegistry.class);
    }

    @Test
    void registerComponentExposesRegisteredComponentUponBuild() {
        TestComponent testComponent = TEST_COMPONENT;
        testSubject.registerComponent(TestComponent.class, c -> testComponent);

        Configuration config = testSubject.build(mock());

        assertEquals(testComponent, config.getComponent(TestComponent.class));
    }

    @Test
    void registerComponentExposesRegisteredComponentOnOptionalGet() {
        TestComponent testComponent = TEST_COMPONENT;
        testSubject.registerComponent(TestComponent.class, c -> testComponent);

        Configuration config = testSubject.build(mock());

        Optional<TestComponent> result = config.getOptionalComponent(TestComponent.class);

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
        testSubject.registerComponent(TestComponent.class, testNameOne, c -> testComponentOne)
                   .registerComponent(TestComponent.class, testNameTwo, c -> testComponentTwo);

        Configuration config = testSubject.build(mock());

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

        Configuration config = testSubject.build(mock());

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

        Configuration config = testSubject.build(mock());

        assertNotEquals(testComponent, config.getComponent(TestComponent.class));
        assertEquals(expectedComponent, config.getComponent(TestComponent.class));
    }

    @Test
    void registeringComponentsForTheSameTypeAndNameReplacesThePreviousComponentBuilder() {
        TestComponent testComponent = new TestComponent("replaced-component");
        TestComponent expectedComponent = new TestComponent("the-winner");
        testSubject.registerComponent(TestComponent.class, "name", c -> testComponent)
                   .registerComponent(TestComponent.class, "name", c -> expectedComponent);

        Configuration config = testSubject.build(mock());

        assertNotEquals(testComponent, config.getComponent(TestComponent.class, "name"));
        assertEquals(expectedComponent, config.getComponent(TestComponent.class, "name"));
    }

    @Test
    void getComponentWithDefaultInvokesSupplierWhenThereIsNoRegisteredComponentForTheGivenClass() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        TestComponent defaultComponent = new TestComponent("default");
        TestComponent registeredComponent = TEST_COMPONENT;
        testSubject.registerComponent(TestComponent.class, "id", c -> registeredComponent);

        Configuration config = testSubject.build(mock());

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
        testSubject.registerModule(
                           new TestModule("test1").componentRegistry(
                                   cr -> cr.registerComponent(
                                           String.class, "child",
                                           c -> c.getComponent(String.class, "parent") + "&child"
                                   )
                           )
                   )
                   .registerComponent(String.class, "parent", c -> "parent");
        Configuration actual = testSubject.build(mock());

        assertEquals("parent", actual.getComponent(String.class, "parent"));
        assertThrows(ComponentNotFoundException.class, () -> actual.getComponent(String.class, "child"));

        assertEquals("parent&child", actual.getModuleConfigurations().getFirst().getComponent(String.class, "child"));
    }

    @Test
    void enhancersAreInvokedInOrder() {
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

        testSubject.registerEnhancer(enhancerWithDefaultOrder)
                   .registerEnhancer(enhancerWithLowOrder)
                   .registerEnhancer(enhancerWithHighOrder);

        testSubject.build(mock());

        InOrder enhancementOrder = inOrder(enhancerWithLowOrder, enhancerWithDefaultOrder, enhancerWithHighOrder);
        enhancementOrder.verify(enhancerWithLowOrder).enhance(any());
        enhancementOrder.verify(enhancerWithDefaultOrder).enhance(any());
        enhancementOrder.verify(enhancerWithHighOrder).enhance(any());
    }

    @Test
    void buildWillScanAndCallEnhancersFromClasspathIfNotDisabled() {
        TestConfigurationEnhancer.withActiveTestEnhancer(() -> {
            testSubject.build(new StubLifecycleRegistry());
            assertTrue(TestConfigurationEnhancer.hasEnhanced());
        });
    }

    @Test
    void disableEnhancerScanningWillDisableAllEnhancers() {
        testSubject.disableEnhancerScanning();

        TestConfigurationEnhancer.withActiveTestEnhancer(() -> {
            testSubject.build(new StubLifecycleRegistry());
            assertFalse(TestConfigurationEnhancer.hasEnhanced());
        });
    }


    @Test
    void disableSpecificEnhancerWillDisableLoadingOfThatEnhancer() {
        testSubject.disableEnhancer(TestConfigurationEnhancer.class);

        TestConfigurationEnhancer.withActiveTestEnhancer(() -> {
            testSubject.build(new StubLifecycleRegistry());
            assertFalse(TestConfigurationEnhancer.hasEnhanced());
        });
    }

    @Test
    void disableSpecificEnhancerByClassNameWillDisableLoadingOfThatEnhancer() {
        var className = TestConfigurationEnhancer.class.getName();
        testSubject.disableEnhancer(className);

        TestConfigurationEnhancer.withActiveTestEnhancer(() -> {
            testSubject.build(new StubLifecycleRegistry());
            assertFalse(TestConfigurationEnhancer.hasEnhanced());
        });
    }

    @Nested
    class ComponentRegistrationAndRetrieval {

        @Nested
        class InterfaceVsImplementationRegistration {

            @Test
            void registerByInterfaceRetrieveByInterface() {
                // given
                ServiceImplA implementation = new ServiceImplA("test-value");
                testSubject.registerComponent(ServiceInterface.class, c -> implementation);

                // when
                Configuration config = testSubject.build(mock());

                // then
                assertTrue(config.hasComponent(ServiceInterface.class));
                ServiceInterface result = config.getComponent(ServiceInterface.class);
                assertEquals("test-value", result.getValue());
                assertSame(implementation, result);
            }

            @Test
            void registerByInterfaceRetrieveByImplementationFails() {
                // given
                ServiceImplA implementation = new ServiceImplA("test-value");
                testSubject.registerComponent(ServiceInterface.class, c -> implementation);

                // when
                Configuration config = testSubject.build(mock());

                // then
                assertFalse(config.hasComponent(ServiceImplA.class));
                assertThrows(ComponentNotFoundException.class, () -> config.getComponent(ServiceImplA.class));
            }

            @Test
            void registerByImplementationIsAlsoRetrievableByInterface() {
                // given
                ServiceImplA implementation = new ServiceImplA("test-value");
                testSubject.registerComponent(ServiceImplA.class, c -> implementation);

                // when
                Configuration config = testSubject.build(mock());

                // then
                // Component registered by implementation class is accessible by implementation
                assertTrue(config.hasComponent(ServiceImplA.class));
                ServiceImplA resultByImpl = config.getComponent(ServiceImplA.class);
                assertEquals("test-value", resultByImpl.getValue());
                assertSame(implementation, resultByImpl);

                // Framework also makes it available by interface automatically
                assertTrue(config.hasComponent(ServiceInterface.class));
                ServiceInterface resultByInterface = config.getComponent(ServiceInterface.class);
                assertEquals("test-value", resultByInterface.getValue());
                assertSame(implementation, resultByInterface);
            }

            @Test
            void registerByImplementationRetrieveByImplementation() {
                // given
                ServiceImplA implementation = new ServiceImplA("test-value");
                testSubject.registerComponent(ServiceImplA.class, c -> implementation);

                // when
                Configuration config = testSubject.build(mock());

                // then
                assertTrue(config.hasComponent(ServiceImplA.class));
                ServiceImplA result = config.getComponent(ServiceImplA.class);
                assertEquals("test-value", result.getValue());
                assertSame(implementation, result);
            }

            @Test
            void registerByInterfaceWithDifferentImplementationsReplacesComponent() {
                // given
                ServiceImplA implA = new ServiceImplA("value-a");
                ServiceImplB implB = new ServiceImplB("value-b");

                testSubject.registerComponent(ServiceInterface.class, c -> implA)
                           .registerComponent(ServiceInterface.class, c -> implB);

                // when
                Configuration config = testSubject.build(mock());

                // then
                assertTrue(config.hasComponent(ServiceInterface.class));
                ServiceInterface result = config.getComponent(ServiceInterface.class);
                assertEquals("value-b", result.getValue());
                assertSame(implB, result);
                assertNotSame(implA, result);
            }
        }

        @Nested
        class MultipleImplementationsWithoutNames {

            @Test
            void registeringMultipleImplementationsWithoutNamesReplacesComponent() {
                // given
                ServiceImplA implA = new ServiceImplA("value-a");
                ServiceImplB implB = new ServiceImplB("value-b");

                testSubject.registerComponent(ServiceInterface.class, c -> implA)
                           .registerComponent(ServiceInterface.class, c -> implB);

                // when
                Configuration config = testSubject.build(mock());

                // then
                assertTrue(config.hasComponent(ServiceInterface.class));
                ServiceInterface result = config.getComponent(ServiceInterface.class);
                assertEquals("value-b", result.getValue());
                assertSame(implB, result);
            }

            @Test
            void hasComponentReturnsTrueOnlyForLastRegisteredComponent() {
                // given
                ServiceImplA implA = new ServiceImplA("value-a");
                ServiceImplB implB = new ServiceImplB("value-b");

                testSubject.registerComponent(ServiceInterface.class, c -> implA)
                           .registerComponent(ServiceInterface.class, c -> implB);

                // when
                Configuration config = testSubject.build(mock());

                // then
                assertTrue(config.hasComponent(ServiceInterface.class));
                // Cannot check by implementation type as registration is by interface
                assertFalse(config.hasComponent(ServiceImplA.class));
                assertFalse(config.hasComponent(ServiceImplB.class));
            }

            @Test
            void getOptionalComponentReturnsOnlyLastRegisteredComponent() {
                // given
                ServiceImplA implA = new ServiceImplA("value-a");
                ServiceImplB implB = new ServiceImplB("value-b");

                testSubject.registerComponent(ServiceInterface.class, c -> implA)
                           .registerComponent(ServiceInterface.class, c -> implB);

                // when
                Configuration config = testSubject.build(mock());

                // then
                Optional<ServiceInterface> result = config.getOptionalComponent(ServiceInterface.class);
                assertTrue(result.isPresent());
                assertEquals("value-b", result.get().getValue());
                assertSame(implB, result.get());
            }

            @Test
            void componentBuilderIsOnlyInvokedForLastRegisteredComponent() {
                // given
                AtomicInteger counterA = new AtomicInteger(0);
                AtomicInteger counterB = new AtomicInteger(0);

                testSubject.registerComponent(ServiceInterface.class, c -> {
                                   counterA.incrementAndGet();
                                   return new ServiceImplA("value-a");
                               })
                           .registerComponent(ServiceInterface.class, c -> {
                               counterB.incrementAndGet();
                               return new ServiceImplB("value-b");
                           });

                // when
                Configuration config = testSubject.build(mock());
                config.getComponent(ServiceInterface.class);

                // then
                assertEquals(0, counterA.get(), "First builder should not be invoked");
                assertEquals(1, counterB.get(), "Second builder should be invoked once");
            }
        }

        @Nested
        class MultipleImplementationsWithNames {

            @Test
            void registerMultipleImplementationsWithDifferentNamesAllAccessible() {
                // given
                ServiceImplA implA = new ServiceImplA("value-a");
                ServiceImplB implB = new ServiceImplB("value-b");

                testSubject.registerComponent(ServiceInterface.class, "service-a", c -> implA)
                           .registerComponent(ServiceInterface.class, "service-b", c -> implB);

                // when
                Configuration config = testSubject.build(mock());

                // then
                assertTrue(config.hasComponent(ServiceInterface.class, "service-a"));
                assertTrue(config.hasComponent(ServiceInterface.class, "service-b"));

                ServiceInterface resultA = config.getComponent(ServiceInterface.class, "service-a");
                assertEquals("value-a", resultA.getValue());
                assertSame(implA, resultA);

                ServiceInterface resultB = config.getComponent(ServiceInterface.class, "service-b");
                assertEquals("value-b", resultB.getValue());
                assertSame(implB, resultB);
            }

            @Test
            void registerWithSameNameReplacesComponent() {
                // given
                ServiceImplA implA = new ServiceImplA("value-a");
                ServiceImplB implB = new ServiceImplB("value-b");

                testSubject.registerComponent(ServiceInterface.class, "service", c -> implA)
                           .registerComponent(ServiceInterface.class, "service", c -> implB);

                // when
                Configuration config = testSubject.build(mock());

                // then
                assertTrue(config.hasComponent(ServiceInterface.class, "service"));
                ServiceInterface result = config.getComponent(ServiceInterface.class, "service");
                assertEquals("value-b", result.getValue());
                assertSame(implB, result);
            }

            @Test
            void namedComponentIsNotAccessibleWithoutName() {
                // given
                ServiceImplA implA = new ServiceImplA("value-a");
                testSubject.registerComponent(ServiceInterface.class, "service-a", c -> implA);

                // when
                Configuration config = testSubject.build(mock());

                // then
                assertTrue(config.hasComponent(ServiceInterface.class, "service-a"));
                assertFalse(config.hasComponent(ServiceInterface.class), "Named component should not be accessible without name");
                assertThrows(ComponentNotFoundException.class,
                           () -> config.getComponent(ServiceInterface.class),
                           "Should throw when retrieving named component without name");
            }

            @Test
            void unnamedComponentIsNotAccessibleByArbitraryName() {
                // given
                ServiceImplA implA = new ServiceImplA("value-a");
                testSubject.registerComponent(ServiceInterface.class, c -> implA);

                // when
                Configuration config = testSubject.build(mock());

                // then
                assertTrue(config.hasComponent(ServiceInterface.class));
                assertFalse(config.hasComponent(ServiceInterface.class, "some-name"));
                assertThrows(ComponentNotFoundException.class,
                           () -> config.getComponent(ServiceInterface.class, "some-name"),
                           "Should throw when retrieving unnamed component with a name");
            }

            @Test
            void canRegisterBothNamedAndUnnamedComponentsOfSameType() {
                // given
                ServiceImplA unnamedImpl = new ServiceImplA("unnamed");
                ServiceImplA namedImpl = new ServiceImplA("named");

                testSubject.registerComponent(ServiceInterface.class, c -> unnamedImpl)
                           .registerComponent(ServiceInterface.class, "named-service", c -> namedImpl);

                // when
                Configuration config = testSubject.build(mock());

                // then
                assertTrue(config.hasComponent(ServiceInterface.class));
                assertTrue(config.hasComponent(ServiceInterface.class, "named-service"));

                ServiceInterface unnamedResult = config.getComponent(ServiceInterface.class);
                assertEquals("unnamed", unnamedResult.getValue());
                assertSame(unnamedImpl, unnamedResult);

                ServiceInterface namedResult = config.getComponent(ServiceInterface.class, "named-service");
                assertEquals("named", namedResult.getValue());
                assertSame(namedImpl, namedResult);
            }

            @Test
            void getOptionalComponentWithNameReturnsCorrectComponent() {
                // given
                ServiceImplA implA = new ServiceImplA("value-a");
                ServiceImplB implB = new ServiceImplB("value-b");

                testSubject.registerComponent(ServiceInterface.class, "service-a", c -> implA)
                           .registerComponent(ServiceInterface.class, "service-b", c -> implB);

                // when
                Configuration config = testSubject.build(mock());

                // then
                Optional<ServiceInterface> resultA = config.getOptionalComponent(ServiceInterface.class, "service-a");
                assertTrue(resultA.isPresent());
                assertEquals("value-a", resultA.get().getValue());

                Optional<ServiceInterface> resultB = config.getOptionalComponent(ServiceInterface.class, "service-b");
                assertTrue(resultB.isPresent());
                assertEquals("value-b", resultB.get().getValue());

                Optional<ServiceInterface> nonExistent = config.getOptionalComponent(ServiceInterface.class, "non-existent");
                assertFalse(nonExistent.isPresent());
            }
        }

        @Nested
        class ComponentNotFoundScenarios {

            @Test
            void retrievingUnregisteredComponentThrowsComponentNotFoundException() {
                // given
                Configuration config = testSubject.build(mock());

                // when / then
                assertThrows(ComponentNotFoundException.class,
                           () -> config.getComponent(ServiceInterface.class));
            }

            @Test
            void retrievingUnregisteredComponentByNameThrowsComponentNotFoundException() {
                // given
                Configuration config = testSubject.build(mock());

                // when / then
                assertThrows(ComponentNotFoundException.class,
                           () -> config.getComponent(ServiceInterface.class, "non-existent"));
            }

            @Test
            void retrievingByWrongTypeThrowsComponentNotFoundException() {
                // given
                testSubject.registerComponent(ServiceInterface.class, c -> new ServiceImplA("test"));

                // when
                Configuration config = testSubject.build(mock());

                // then
                assertThrows(ComponentNotFoundException.class,
                           () -> config.getComponent(String.class),
                           "Should throw when requesting component by wrong type");
            }

            @Test
            void getOptionalComponentReturnsEmptyForNonExistentComponent() {
                // given
                Configuration config = testSubject.build(mock());

                // when
                Optional<ServiceInterface> result = config.getOptionalComponent(ServiceInterface.class);

                // then
                assertFalse(result.isPresent());
            }

            @Test
            void getOptionalComponentReturnsEmptyForNonExistentNamedComponent() {
                // given
                testSubject.registerComponent(ServiceInterface.class, c -> new ServiceImplA("test"));
                Configuration config = testSubject.build(mock());

                // when
                Optional<ServiceInterface> result = config.getOptionalComponent(ServiceInterface.class, "non-existent");

                // then
                assertFalse(result.isPresent());
            }

            @Test
            void hasComponentReturnsFalseForUnregisteredComponent() {
                // given
                Configuration config = testSubject.build(mock());

                // when / then
                assertFalse(config.hasComponent(ServiceInterface.class));
            }

            @Test
            void hasComponentReturnsFalseForUnregisteredNamedComponent() {
                // given
                testSubject.registerComponent(ServiceInterface.class, c -> new ServiceImplA("test"));
                Configuration config = testSubject.build(mock());

                // when / then
                assertFalse(config.hasComponent(ServiceInterface.class, "non-existent"));
            }

            @Test
            void getComponentWithDefaultSupplierReturnsDefaultForNonExistentComponent() {
                // given
                Configuration config = testSubject.build(mock());
                ServiceImplA defaultImpl = new ServiceImplA("default");

                // when
                ServiceInterface result = config.getComponent(ServiceInterface.class, () -> defaultImpl);

                // then
                assertSame(defaultImpl, result);
                assertEquals("default", result.getValue());
            }

            @Test
            void getComponentWithDefaultSupplierReturnsRegisteredComponentWhenExists() {
                // given
                ServiceImplA registeredImpl = new ServiceImplA("registered");
                ServiceImplA defaultImpl = new ServiceImplA("default");
                testSubject.registerComponent(ServiceInterface.class, c -> registeredImpl);

                // when
                Configuration config = testSubject.build(mock());
                ServiceInterface result = config.getComponent(ServiceInterface.class, () -> defaultImpl);

                // then
                assertSame(registeredImpl, result);
                assertEquals("registered", result.getValue());
                assertNotSame(defaultImpl, result);
            }
        }
    }

    @Nested
    class DescribeTo {

        @Test
        void configurerDescribeToDescribesBeingUninitializedComponentsEnhancersAndModules() {
            // given...
            ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);
            ConfigurationEnhancer testEnhancer = configurer -> {

            };
            TestModule testModule = new TestModule("module");
            //noinspection unchecked
            ComponentFactory<String> testFactory = mock(ComponentFactory.class);

            testSubject.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                       .registerEnhancer(testEnhancer)
                       .registerModule(testModule)
                       .registerFactory(testFactory);

            // when...
            testSubject.describeTo(testDescriptor);

            // then...
            verify(testDescriptor).describeProperty("initialized", false);
            verify(testDescriptor).describeProperty(eq("components"), isA(Components.class));
            verify(testDescriptor).describeProperty(eq("decorators"), isA(List.class));
            verify(testDescriptor).describeProperty(eq("modules"), eqList(List.of(testModule)));
            //noinspection unchecked
            ArgumentCaptor<Map<String, ConfigurationEnhancer>> enhancerCaptor = ArgumentCaptor.forClass(Map.class);
            verify(testDescriptor).describeProperty(eq("configurerEnhancers"), enhancerCaptor.capture());
            Collection<ConfigurationEnhancer> enhancers = enhancerCaptor.getValue().values();
            assertTrue(enhancers.contains(testEnhancer));
            //noinspection unchecked
            ArgumentCaptor<List<ComponentFactory<?>>> factoryCaptor = ArgumentCaptor.forClass(List.class);
            verify(testDescriptor).describeProperty(eq("factories"), factoryCaptor.capture());
            List<ComponentFactory<?>> factories = factoryCaptor.getValue();
            assertTrue(factories.contains(testFactory));

            // Ensure new fields added to the describeTo implementation are validated
            verifyNoMoreInteractions(testDescriptor);
        }

        @Test
        void configurerDescribeToDescribesBeingInitializedComponentsEnhancersAndModules() {
            // given...
            ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);
            ConfigurationEnhancer testEnhancer = configurer -> {

            };
            TestModule testModule = new TestModule("module");
            //noinspection unchecked
            ComponentFactory<String> testFactory = mock(ComponentFactory.class);
            when(testFactory.forType()).thenReturn(String.class);

            testSubject.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                       .registerEnhancer(testEnhancer)
                       .registerModule(testModule)
                       .registerFactory(testFactory);
            // Build initializes the configurer
            testSubject.build(lifecycleRegistry);

            // when...
            testSubject.describeTo(testDescriptor);

            // then...
            verify(testDescriptor).describeProperty("initialized", true);
            verify(testDescriptor).describeProperty(eq("components"), isA(Components.class));
            verify(testDescriptor).describeProperty(eq("decorators"), isA(List.class));
            verify(testDescriptor).describeProperty(eq("modules"), eqList(testModule));
            //noinspection unchecked
            ArgumentCaptor<Map<String, ConfigurationEnhancer>> enhancerCaptor = ArgumentCaptor.forClass(Map.class);
            verify(testDescriptor).describeProperty(eq("configurerEnhancers"), enhancerCaptor.capture());
            Collection<ConfigurationEnhancer> enhancers = enhancerCaptor.getValue().values();
            assertTrue(enhancers.contains(testEnhancer));
            //noinspection unchecked
            ArgumentCaptor<List<ComponentFactory<?>>> factoryCaptor = ArgumentCaptor.forClass(List.class);
            verify(testDescriptor).describeProperty(eq("factories"), factoryCaptor.capture());
            List<ComponentFactory<?>> factories = factoryCaptor.getValue();
            assertTrue(factories.contains(testFactory));

            // Ensure new fields added to the describeTo implementation are validated
            verifyNoMoreInteractions(testDescriptor);
        }

        @Test
        void configurationDescribeToDescribesComponentsAndModules() {
            ComponentDescriptor testDescriptor = mock(ComponentDescriptor.class);

            TestModule testModule = new TestModule("module");

            // The Component is not yet validated, as I am using a mocked ComponentDescriptor.
            testSubject.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                       .registerModule(testModule.componentRegistry(
                               cr -> cr.registerComponent(TestComponent.class, c -> TEST_COMPONENT)
                       ));

            Configuration result = testSubject.build(mock());

            result.describeTo(testDescriptor);

            verify(testDescriptor).describeProperty(eq("components"), isA(Components.class));
            verify(testDescriptor).describeProperty(eq("modules"), eqList(result.getModuleConfigurations()));

            // Ensure new fields added to the describeTo implementation are validated
            verifyNoMoreInteractions(testDescriptor);
        }

        @SafeVarargs
        private <T> Collection<T> eqList(T... expected) {
            return eqList(List.of(expected));
        }

        private <T> Collection<T> eqList(Collection<T> expected) {
            return argThat(c -> List.copyOf(expected).equals(List.copyOf(c)));
        }
    }

    protected record TestComponent(String state) {

    }

    private record RegisteringConfigurationEnhancer(
            List<ConfigurationEnhancer> invokedEnhancers,
            int order
    ) implements ConfigurationEnhancer {

        @Override
        public void enhance(@Nonnull ComponentRegistry registry) {
            invokedEnhancers.add(this);
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

    // Test component hierarchy for exploration tests
    interface ServiceInterface {
        String getValue();
    }

    static class ServiceImplA implements ServiceInterface {
        private final String value;

        ServiceImplA(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    static class ServiceImplB implements ServiceInterface {
        private final String value;

        ServiceImplB(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}