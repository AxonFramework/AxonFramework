/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.config;

import java.util.List;

import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Assertion utils for axon {@link Configuration}.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class ConfigAssertions {

    private ConfigAssertions() {
        // not meant to be instantiated
    }

    /**
     * Asserts whether given {@code modules} are present in the provided {@code configuration}.
     *
     * @param configuration Axon configuration
     * @param moduleTypes   expected module configuration types present in {@code configuration}
     */
    @SafeVarargs
    public static void assertExpectedModules(Configuration configuration,
                                             Class<? extends ModuleConfiguration>... moduleTypes) {
        stream(moduleTypes).forEach(moduleType -> {
            List<? extends ModuleConfiguration> matches = configuration.findModules(moduleType);
            if (matches.isEmpty()) {
                fail("No module of type '" + moduleType.getName() + "' was found in the provided configuration");
            }
        });
    }
}
