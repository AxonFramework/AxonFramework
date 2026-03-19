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

package org.axonframework.test.fixture;

/**
 * Generic per-fixture extension point that allows customization of the components used by each
 * {@link AxonTestFixture} instance.
 * <p>
 * A {@code FixtureCustomizer} is registered as a component in the
 * {@link org.axonframework.common.configuration.Configuration} and is invoked once per fixture construction. It
 * receives the resolved {@link FixtureConfiguration} and returns a (possibly wrapped) version. This enables
 * cross-cutting concerns like test isolation to wrap the command bus, event sink, and recording registry without
 * the fixture itself being aware of the customization.
 * <p>
 * Example usage with {@link TestIsolationEnhancer}:
 * <pre>{@code
 * var configurer = MessagingConfigurer.create()
 *         .componentRegistry(cr -> cr.registerEnhancer(new TestIsolationEnhancer()));
 * var fixture = AxonTestFixture.with(configurer);
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see TestIsolationEnhancer
 * @see FixtureConfiguration
 */
@FunctionalInterface
public interface FixtureCustomizer {

    /**
     * Customizes the given {@code components} for a single fixture instance.
     * <p>
     * Called once per {@link AxonTestFixture} construction. Implementations may wrap or replace any of the components
     * to add per-fixture behavior (e.g., test isolation metadata stamping and filtering).
     *
     * @param configuration The resolved fixture configuration from the application configuration.
     * @return The customized configuration to be used by the fixture. Must not be {@code null}.
     */
    FixtureConfiguration customize(FixtureConfiguration configuration);
}
