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

package org.axonframework.update.configuration;

import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentVariableUsagePropertyProviderTest {

    private final Map<String, String> env = new HashMap<>();
    private EnvironmentVariableUsagePropertyProvider provider;

    @BeforeEach
    void setup() {
        env.clear();
        provider = new EnvironmentVariableUsagePropertyProvider(env::get);
    }

    @Test
    void getDisabledTrue() {
        env.put(EnvironmentVariableUsagePropertyProvider.DISABLED_KEY, "true");
        assertTrue(provider.getDisabled());
    }

    @Test
    void getDisabledFalse() {
        env.put(EnvironmentVariableUsagePropertyProvider.DISABLED_KEY, "false");
        assertFalse(provider.getDisabled());
    }

    @Test
    void getDisabledNullWhenUnset() {
        assertNull(provider.getDisabled());
    }

    @Test
    void getUrl() {
        env.put(EnvironmentVariableUsagePropertyProvider.URL_KEY, "https://env.url");
        assertEquals("https://env.url", provider.getUrl());
    }

    @Test
    void getUrlNullWhenUnset() {
        assertNull(provider.getUrl());
    }

    @Test
    void priority() {
        assertEquals(Integer.MAX_VALUE / 2, provider.priority());
    }
}
