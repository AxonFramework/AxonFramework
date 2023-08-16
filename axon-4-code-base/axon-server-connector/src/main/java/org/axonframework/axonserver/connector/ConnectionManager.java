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

package org.axonframework.axonserver.connector;

import java.util.Map;

/**
 * Interface describing functionality for connection managers. A connection manager typically deals with a collection of
 * connections per context the application is wired with.
 *
 * @author Steven van Beelen
 * @author Milan Savic
 * @author Sara Pelligrini
 * @since 4.6.0
 */
public interface ConnectionManager {

    /**
     * Return the connections this instances manages. Consists of key-value pairs where the key resembles the context
     * name and the value describes whether the connection is active at this moment.
     *
     * @return Return the connections this instances manages.
     */
    Map<String, Boolean> connections();
}
