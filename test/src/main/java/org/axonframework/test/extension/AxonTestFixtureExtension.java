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

package org.axonframework.test.extension;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.test.FixtureExecutionException;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.extension.ExtensionContext.*;

public class AxonTestFixtureExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final Namespace NAMESPACE = Namespace.create(AxonTestFixtureExtension.class);
    private static final String FIXTURE_KEY = "axonTestFixture";

    @Override
    public void beforeEach(@Nonnull ExtensionContext extensionContext) throws Exception {
        Object testInstance = extensionContext.getRequiredTestInstance();
        Class<?> testClass = extensionContext.getRequiredTestClass();

        ProvidedAxonTestFixtureUtils.findProvider(testInstance,
                                                  testClass,
                                                  extensionContext.getRequiredTestMethod())
                                    .ifPresent(provider -> extensionContext.getStore(NAMESPACE)
                                                                  .put(FIXTURE_KEY, provider.get()));
    }

    @Override
    public void afterEach(@Nonnull ExtensionContext extensionContext) {
        AxonTestFixture fixture = extensionContext.getStore(NAMESPACE).get(FIXTURE_KEY, AxonTestFixture.class);
        if (fixture != null) {
            fixture.stop();
        }
    }

    @Override
    public boolean supportsParameter(@Nonnull ParameterContext parameterContext,
                                     @Nonnull ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return AxonTestFixture.class.isAssignableFrom(parameterContext.getParameter().getType());
    }

    @Override
    public @Nullable Object resolveParameter(@Nonnull ParameterContext parameterContext,
                                             @Nonnull ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Object fixture = extensionContext.getStore(NAMESPACE).get(FIXTURE_KEY, AxonTestFixture.class);
        if (fixture == null) {
            throw new ParameterResolutionException(
                    "No AxonTestFixture provider found. Please provide one using the @ProvidedAxonTestFixture annotation.");
        }
        return fixture;
    }
}
