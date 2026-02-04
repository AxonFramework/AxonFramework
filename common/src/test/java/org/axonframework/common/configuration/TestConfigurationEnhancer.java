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

package org.axonframework.common.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.ConfigurationEnhancer;

/**
 * Classpath {@link ConfigurationEnhancer} registered through the serviceloader mechanism in tests. Useful for testing
 * whether registrations are picked up or not.
 * <p>
 * Wrap the building of the configuration in a call to {@link #withActiveTestEnhancer(Runnable)} to activate this
 * enhancer. You can then use {@link #hasEnhanced()} to check whether the enhancer was called.
 *
 * @since 5.0.0
 * @author Mitchell Herrijgers
 */
public class TestConfigurationEnhancer implements ConfigurationEnhancer {

    private static boolean active = false;
    private static boolean hasEnhanced = false;

    public static void withActiveTestEnhancer(Runnable runnable) {
        active = true;
        try {
            runnable.run();
        } finally {
            active = false;
            hasEnhanced = false;
        }
    }

    public static boolean hasEnhanced() {
        return hasEnhanced;
    }

    @Override
    public void enhance(@Nonnull ComponentRegistry configurer) {
        if (active) {
            hasEnhanced = true;
        }
    }
}
