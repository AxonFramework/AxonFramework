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

package org.axonframework.integrationtests.testsuite;

import org.axonframework.test.server.AxonServerContainer;

public final class SharedAxonServerContainer {

    private static volatile AxonServerContainer instance;

    private SharedAxonServerContainer() {
    }

    public static AxonServerContainer getInstance() {
        if (instance == null) {
            synchronized (SharedAxonServerContainer.class) {
                if (instance == null) {
                    instance = new AxonServerContainer("docker.axoniq.io/axoniq/axonserver:2025.2.0-EAP2")
                            .withAxonServerHostname("localhost")
                            .withDevMode(true)
                            .withReuse(true)
                            .withLabel("reuse-hash", "axon-server-integration-tests");
                }
            }
        }
        return instance;
    }
}