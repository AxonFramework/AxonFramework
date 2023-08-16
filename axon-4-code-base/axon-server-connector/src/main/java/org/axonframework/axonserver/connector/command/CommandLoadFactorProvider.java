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

package org.axonframework.axonserver.connector.command;

/**
 * Provides the load factor value of the client for the specific command. This information is used from AxonServer in
 * order to balance the load among client instances.
 *
 * @author Sara Pellegrini
 * @since 4.3
 */
@FunctionalInterface
public interface CommandLoadFactorProvider {

    /**
     * The default value for command load factor: 100. It represents the fixed value of load factor sent to Axon Server
     * for any command's subscription if no specific implementation of CommandLoadFactorProvider is configured.
     */
    int DEFAULT_VALUE = 100;

    /**
     * Returns the load factor value for the specific command
     *
     * @param command the command name
     * @return the load factor value for the specific command
     */
    int getFor(String command);
}
