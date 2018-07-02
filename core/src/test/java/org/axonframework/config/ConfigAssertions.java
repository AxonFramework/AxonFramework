/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.config;

import java.util.List;

import static org.junit.Assert.*;

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
     * @param modules       Module configurations
     */
    @SafeVarargs
    public static void assertExpectedModules(Configuration configuration,
                                             Class<? extends ModuleConfiguration>... modules) {
        List<ModuleConfiguration> configurationModules = configuration.getModules();
        boolean[] matches = new boolean[configurationModules.size()];
        for (Class<? extends ModuleConfiguration> module : modules) {
            boolean found = false;
            for (int i = 0; i < configurationModules.size(); i++) {
                if (module.isInstance(configurationModules.get(i)) && !matches[i]) {
                    found = true;
                    matches[i] = true;
                    break;
                }
            }
            if (!found) {
                fail("Module '" + module + "' was not found in the provided configuration");
                break;
            }
        }
    }
}
