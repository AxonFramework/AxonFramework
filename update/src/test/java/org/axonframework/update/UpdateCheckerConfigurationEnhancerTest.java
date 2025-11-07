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

package org.axonframework.update;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.common.configuration.DefaultComponentRegistry;
import org.axonframework.update.configuration.UsagePropertyProvider;
import org.axonframework.common.util.StubLifecycleRegistry;
import org.junit.jupiter.api.*;

import static org.axonframework.update.detection.TestEnvironmentDetector.AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT;
import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerConfigurationEnhancerTest {

    @BeforeEach
    void setUp() {
        System.setProperty(AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT, "true");
    }

    @AfterEach
    void tearDown() {
        // Reset the system property after tests
        System.clearProperty(AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT);
    }

    @Test
    void configureShouldConfigureComponentsWithStartAndShutdown() {
        UpdateCheckerConfigurationEnhancer enhancer = new UpdateCheckerConfigurationEnhancer();
        DefaultComponentRegistry componentRegistry = new DefaultComponentRegistry();
        assertDoesNotThrow(() -> enhancer.enhance(componentRegistry));

        assertTrue(componentRegistry.hasComponent(UsagePropertyProvider.class));
        assertTrue(componentRegistry.hasComponent(UpdateCheckerHttpClient.class));
        assertTrue(componentRegistry.hasComponent(UpdateCheckerReporter.class));
        assertTrue(componentRegistry.hasComponent(UpdateChecker.class));

        StubLifecycleRegistry lifecycleRegistry = new StubLifecycleRegistry();
        Configuration config = componentRegistry.build(lifecycleRegistry);

        UpdateChecker checker = config.getComponent(UpdateChecker.class);
        assertFalse(checker.isStarted());

        lifecycleRegistry.start(config);

        assertTrue(checker.isStarted());

        lifecycleRegistry.shutdown(config);

        assertFalse(checker.isStarted());
    }
}