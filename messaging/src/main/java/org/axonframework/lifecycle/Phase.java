/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.lifecycle;

/**
 * Utility class containing constants which can be used as input for the {@link StartHandler} and {@link
 * ShutdownHandler} annotations.
 *
 * @author Steven van Beelen
 * @see StartHandler
 * @see ShutdownHandler
 * @since 4.3
 */
public abstract class Phase {

    private Phase() {
        // Utility class
    }

    /**
     * Phase to start or shutdown inbound command and/or query connectors. It is targeted towards connectors which
     * receive commands and/or queries from external applications.
     */
    public static final int INBOUND_COMMAND_OR_QUERY_CONNECTOR = 5_000;
    /**
     * Phase to start or shutdown outbound event connectors. It is targeted towards connectors which can send events out
     * to external applications.
     */
    public static final int OUTBOUND_EVENT_CONNECTORS = 5_000;
    /**
     * Phase to start or shutdown outbound command and/or query connectors. It is targeted towards connectors which send
     * commands and/or queries out to external applications.
     */
    public static final int OUTBOUND_COMMAND_OR_QUERY_CONNECTORS = 10_000;
    /**
     * Phase to start or shutdown inbound event connectors. It is targeted towards connectors which can receive events
     * from external sources.
     */
    public static final int INBOUND_EVENT_CONNECTORS = 15_000;
}
