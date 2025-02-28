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

/**
 * Test suite validating the common behavior of each {@link Module} implementation.
 * <p>
 * This test suite includes the {@link ConfigurerTestSuite}, since each module is a configurer implementation.
 *
 * @param <M> The {@link Module} implementation under test.
 * @author Steven van Beelen
 */
public abstract class ModuleTestSuite<M extends Module<M>> extends ConfigurerTestSuite<M> {

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
}
