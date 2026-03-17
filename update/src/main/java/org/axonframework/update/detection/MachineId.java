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

package org.axonframework.update.detection;

import org.axonframework.common.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Loads and stores a unique machine ID in the user's home directory.
 * This ID is used to identify the machine for usage tracking purposes.
 * If the ID can't be stored or read, it will generate a new UUID.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public class MachineId {
    private static final Logger logger = LoggerFactory.getLogger(MachineId.class);
    private static final String MACHINE_ID_PATH = "/.axoniq/.machine-id";

    private String machineId;

    /**
     * Creates a new instance of {@code MachineId}.
     */
    public MachineId() {
        this.initialize();
    }

    /**
     * Returns the unique machine ID. If it has not been initialized yet, it will initialize it first.
     *
     * @return The unique machine ID.
     */
    public String get() {
        return machineId;
    }

    private void initialize() {
        try {
            File file = getFile();
            if(file == null) {
                logger.debug("Could not determine user home directory. Machine ID will not be stored.");
                machineId = UUID.randomUUID().toString();
                return;
            }
            if (file.exists()) {
                machineId = new String(Files.readAllBytes(file.toPath()));
                return;
            }
            File parentDir = getFile().getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw new IOException("Failed to create parent directory: " + parentDir.getAbsolutePath());
            }
            machineId = UUID.randomUUID().toString();
            Files.writeString(file.toPath(), machineId);
        } catch (Exception e) {
            logger.debug("Failed to initialize machine id", e);
        }
    }

    private File getFile() {
        String pwdDir = System.getProperty("user.home");
        if(pwdDir == null) {
            return null;
        }
        String installationIdFilePath = pwdDir + MACHINE_ID_PATH;
        return new File(installationIdFilePath);
    }
}
