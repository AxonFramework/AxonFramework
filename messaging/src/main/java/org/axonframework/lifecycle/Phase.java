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
 * ShutdownHandler} annotations, or components implementing the {@link Lifecycle} interface.
 *
 * @author Steven van Beelen
 * @see Lifecycle
 * @see StartHandler
 * @see ShutdownHandler
 * @since 4.3
 */
public abstract class Phase {

    private Phase() {
        // Utility class
    }

    /**
     * Phase to start or shutdown all external connections.
     */
    public static final int EXTERNAL_CONNECTIONS = Integer.MIN_VALUE >> 4;
    /**
     * Phase to start or shutdown outbound event connectors. It is targeted towards connectors which can send events out
     * to external applications.
     */
    public static final int OUTBOUND_EVENT_CONNECTORS = -10;
    /**
     * Phase to register or cancel the registration of any local message handler.
     */
    public static final int LOCAL_MESSAGE_HANDLER_REGISTRATIONS = 0;
    /**
     * Phase to start or shutdown outbound command and/or query connectors. It is targeted towards connectors which send
     * commands and/or queries out to external applications.
     */
    public static final int OUTBOUND_COMMAND_CONNECTORS = 0;
    /**
     * Phase to start or shutdown outbound command and/or query connectors. It is targeted towards connectors which send
     * commands and/or queries out to external applications.
     */
    public static final int OUTBOUND_QUERY_CONNECTORS = 0;
    /**
     * Phase to start or shutdown inbound command connectors. It is targeted towards connectors which receive commands
     * from external applications.
     */
    public static final int INBOUND_COMMAND_CONNECTOR = Integer.MAX_VALUE >> 2;
    /**
     * Phase to start or shutdown inbound query connectors. It is targeted towards connectors which receive queries from
     * external applications.
     */
    public static final int INBOUND_QUERY_CONNECTOR = Integer.MAX_VALUE >> 2;
    /**
     * Phase to start or shutdown inbound event connectors. It is targeted towards connectors which can receive events
     * from external sources.
     */
    public static final int INBOUND_EVENT_CONNECTORS = Integer.MAX_VALUE >> 1;
    /**
     * Phase to start or shutdown components which send/receive/handle instructions for/to other connectors.
     */
    public static final int INSTRUCTION_COMPONENTS = INBOUND_EVENT_CONNECTORS + 10;
}
