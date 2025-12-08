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

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link PropertyFileUsagePropertyProvider}.
 *
 * @author Mitchell Herrijgers
 */
class PropertyFileUsagePropertyProviderTest {

    private final PropertyFileUsagePropertyProvider provider = new PropertyFileUsagePropertyProvider();
    private Path tempHome;
    private File propFile;

    @BeforeEach
    void setup() throws Exception {
        tempHome = Files.createTempDirectory("axon-test-home");
        System.setProperty("user.home", tempHome.toString());
        propFile = new File(tempHome.toString() + "/.axoniq/update-checker.properties");
        if (propFile.exists()) {
            propFile.delete();
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        System.clearProperty("user.home");
        if (propFile.exists()) {
            propFile.delete();
        }
        File parent = propFile.getParentFile();
        if (parent.exists()) {
            parent.delete();
        }
        Files.deleteIfExists(tempHome);
    }

    @Test
    void readsExistingPropertiesFile() throws Exception {
        Properties props = new Properties();
        props.setProperty("telemetry_url", "https://custom.url");
        props.setProperty("disabled", "true");
        propFile.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(propFile)) {
            props.store(out, "Test");
        }
        assertTrue(provider.getDisabled());
        assertEquals("https://custom.url", provider.getUrl());
    }

    @Test
    void createsFileWithDefaultValuesIfNotExists() throws Exception {
        assertFalse(propFile.exists());
        // Should create the file
        assertEquals(DefaultUsagePropertyProvider.INSTANCE.getDisabled(), provider.getDisabled());
        assertEquals(DefaultUsagePropertyProvider.INSTANCE.getUrl(), provider.getUrl());
        assertTrue(propFile.exists());
        Properties props = new Properties();
        try (var in = Files.newInputStream(propFile.toPath())) {
            props.load(in);
        }
        assertEquals(DefaultUsagePropertyProvider.INSTANCE.getUrl(), props.getProperty("telemetry_url"));
        assertEquals(String.valueOf(DefaultUsagePropertyProvider.INSTANCE.getDisabled()),
                     props.getProperty("disabled"));
    }
}
