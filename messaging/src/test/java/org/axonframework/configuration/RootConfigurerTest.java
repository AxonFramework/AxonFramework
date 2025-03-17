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

import static org.junit.jupiter.api.Assertions.*;

import jakarta.annotation.Nullable;
import org.junit.jupiter.api.*;

/**
 * Test class validating the {@link RootConfigurer}.
 *
 * @author Steven van Beelen
 */
class RootConfigurerTest extends ConfigurerTestSuite<RootConfigurer> {

    @Override
    public RootConfigurer testSubject() {
        return RootConfigurer.defaultConfigurer();
    }

    @Nullable
    @Override
    public <D extends NewConfigurer<D>> Class<D> delegateType() {
        return null;
    }

    @Test
    void registerComponentExposesRegisteredComponentUponStart() {
        TestComponent testComponent = TEST_COMPONENT;

        testSubject.registerComponent(TestComponent.class, c -> testComponent);

        RootConfiguration config = testSubject.start();

        assertEquals(testComponent, config.getComponent(TestComponent.class));
    }

    @Test
    void registerOverrideBehaviorThrowResultsInComponentOverrideExceptionsOnOverriding() {
        testSubject.registerOverrideBehavior(OverrideBehavior.THROW)
                   .registerComponent(TestComponent.class, c -> TEST_COMPONENT);

        assertThrows(ComponentOverrideException.class,
                     () -> testSubject.registerComponent(TestComponent.class, c -> TEST_COMPONENT));
    }

    @Test
    void registerOverrideBehaviorAllowResultsInNothingWhenOverriding() {
        testSubject.registerOverrideBehavior(OverrideBehavior.ALLOW)
                   .registerComponent(TestComponent.class, c -> TEST_COMPONENT);

        assertDoesNotThrow(() -> testSubject.registerComponent(TestComponent.class, c -> TEST_COMPONENT));
    }
}