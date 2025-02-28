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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite validating the common behavior of each {@link Module} implementation.
 * <p>
 * This test suite includes the {@link ConfigurerTestSuite}, since each module is a configurer implementation.
 * Furthermore, it implements the {@link ConfigurerTestSuite#testSubject()} operation, constructing a
 * {@link RootConfigurer} as the parent of the module under test.
 *
 * @param <M> The {@link Module} implementation under test.
 * @author Steven van Beelen
 */
public abstract class ModuleTestSuite<M extends Module<M>> extends ConfigurerTestSuite<M> {

    private static final Runnable NO_OP = () -> {
    };

    private M testModule;

    @Override
    public M testSubject() {
        RootConfigurer.defaultConfigurer()
                      .registerModule(config -> {
                          M testModule = testSubject(config);
                          this.testModule = testModule;
                          return testModule;
                      });
        return testModule;
    }

    /**
     * Construct the test {@link Module} of type {@code M} used for testing.
     *
     * @return The test {@link Module} of type {@code M} used for testing.
     */
    abstract M testSubject(LifecycleSupportingConfiguration config);

    @Test
    void canRetrieveComponentsFromParentConfiguration() {
        TestComponent expected = new TestComponent("parent-retrieval-test");
        RootConfigurer.defaultConfigurer()
                      .registerModule(config -> {
                          M testModule = testSubject(config);
                          this.testModule = testModule;
                          return testModule;
                      })
                      .registerComponent(TestComponent.class, "parent-retrieval-test", config -> expected);

        TestComponent result = testModule.build()
                                         .getComponent(TestComponent.class, "parent-retrieval-test");

        assertEquals(expected, result);
    }

    @Test
    void returnsEmptyOptionalIfBothModuleAndParentDoNotHaveTheRequestedComponent() {
        RootConfigurer.defaultConfigurer()
                      .registerModule(config -> {
                          M testModule = testSubject(config);
                          this.testModule = testModule;
                          return testModule;
                      })
                      .registerComponent(TestComponent.class, "parent", config -> new TestComponent("parent"));
        testModule.registerComponent(TestComponent.class, "module", config -> new TestComponent("module"));

        Optional<Object> result = testModule.build()
                                            .getOptionalComponent(Object.class);

        assertFalse(result.isPresent());
    }

    @Test
    void onStartThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> testModule.onStart(0, NO_OP));
    }

    @Test
    void onShutdownThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> testModule.onShutdown(0, NO_OP));
    }
}
