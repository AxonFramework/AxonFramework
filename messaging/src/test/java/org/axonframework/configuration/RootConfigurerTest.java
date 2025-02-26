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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the hierarchy of {@link NewConfigurer} instances that become part of a {@link RootConfigurer}.
 *
 * @author Steven van Beelen
 */
class RootConfigurerTest {

    private static final String INIT_STATE = "initial-state";
    private static final TestComponent TEST_COMPONENT = new TestComponent(INIT_STATE);

    private RootConfigurer testSubject;

    @BeforeEach
    void setUp() {
        testSubject = RootConfigurer.defaultConfigurer();
    }

    @Test
    void registerComponentExposesRegisteredComponentUponBuild() {
        TestComponent testComponent = TEST_COMPONENT;

        testSubject.registerComponent(TestComponent.class, config -> testComponent);

        RootConfiguration config = testSubject.build();

        assertEquals(testComponent, config.getComponent(TestComponent.class));
    }

    @Test
    void registerComponentExposesRegisteredComponentUponStart() {
        TestComponent testComponent = TEST_COMPONENT;

        testSubject.registerComponent(TestComponent.class, config -> testComponent);

        RootConfiguration config = testSubject.start();

        assertEquals(testComponent, config.getComponent(TestComponent.class));
    }

    @Test
    void registerComponentThrowsNullPointerExceptionForNullType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.registerComponent(null, config -> new Object()));
    }

    @Test
    void registerComponentThrowsNullPointerExceptionForComponentBuilder() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> testSubject.registerComponent(TestComponent.class, null));
    }

    @Test
    void registerDecoratorDecoratesOutcomeOfComponentBuilder() {
        String expectedState = "updated-" + TEST_COMPONENT.state() + "-twice";

        testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT)
                   .registerDecorator(TestComponent.class,
                                      (config, delegate) -> new TestComponent("updated-" + delegate.state))
                   .registerDecorator(TestComponent.class,
                                      (config, delegate) -> new TestComponent(delegate.state + "-twice"));

        TestComponent result = testSubject.start()
                                          .getComponent(TestComponent.class);

        assertEquals(expectedState, result.state());
    }

    // TODO Discuss if we would want to throw an exception or whether we should simply store it if the register component comes in later
    @Test
    void registerDecoratorThrowsIllegalArgumentExceptionForNonExistingComponentType() {
        assertThrows(IllegalArgumentException.class,
                     () -> testSubject.registerDecorator(TestComponent.class, (config, delegate) -> delegate));
    }

    @Test
    void registerDecoratorThrowsNullPointerExceptionForType() {
        testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT);

        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> testSubject.registerDecorator(null, (config, delegate) -> delegate));
    }

    @Test
    void registerDecoratorThrowsNullPointerExceptionForNullComponentDecorator() {
        testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT);

        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> testSubject.registerDecorator(TestComponent.class, null));
    }

    @Test
    void registerDecoratorWithOrderDecoratesOutcomeOfComponentBuilderInSpecifiedOrder() {
        String expectedState = TEST_COMPONENT.state() + "123";

        testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT)
                   .registerDecorator(TestComponent.class, 2,
                                      (config, delegate) -> new TestComponent(delegate.state + "3"))
                   .registerDecorator(TestComponent.class, 1,
                                      (config, delegate) -> new TestComponent(delegate.state + "2"))
                   .registerDecorator(TestComponent.class, 0,
                                      (config, delegate) -> new TestComponent(delegate.state + "1"));

        TestComponent result = testSubject.start()
                                          .getComponent(TestComponent.class);

        assertEquals(expectedState, result.state());
    }

    // TODO Discuss if we would want to throw an exception or whether we should simply store it if the register component comes in later
    @Test
    void registerDecoratorWithOrderThrowsIllegalArgumentExceptionForNonExistingComponentType() {
        assertThrows(IllegalArgumentException.class,
                     () -> testSubject.registerDecorator(TestComponent.class, 42, (config, delegate) -> delegate));
    }

    @Test
    void registerDecoratorWithOrderThrowsNullPointerExceptionForType() {
        testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT);

        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> testSubject.registerDecorator(null, 42, (config, delegate) -> delegate));
    }

    @Test
    void registerDecoratorWithOrderThrowsNullPointerExceptionForNullComponentDecorator() {
        testSubject.registerComponent(TestComponent.class, config -> TEST_COMPONENT);

        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> testSubject.registerDecorator(TestComponent.class, 42, null));
    }

    private record TestComponent(String state) {

    }
}