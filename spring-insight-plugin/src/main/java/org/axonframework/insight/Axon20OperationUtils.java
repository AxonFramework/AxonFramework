/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.insight;

import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationMap;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.Message;
import org.axonframework.domain.MetaData;

import java.util.Map.Entry;

/**
 * Operation populating helper methods specific to Axon 2
 * {@link Message} types.
 *
 * @author Joris Kuipers
 * @since 2.0
 */
public class Axon20OperationUtils {

    /**
     * Adds {@link MetaData} from the {@link Message} to the {@link Operation}.
     *
     * @param operation the operation to add meta data to
     * @param message   can be null, in which case nothing happens
     */
    static void addMetaDataTo(Operation operation, Message<?> message) {
        if (message != null) {
            addMetaDataTo(operation, message.getMetaData());
        }
    }

    /**
     * Processes the event handler arguments
     * by populating the given {@link Operation}
     *
     * @param args      The arguments of the operation
     * @param operation The operation
     * @return true if first arg was {@link EventMessage} and has been handled,
     *         false if calling code still needs to handle it as an event.
     */
    static boolean processEventMessage(Object[] args, Operation operation) {
        if (!(args[0] instanceof EventMessage<?>)) {
            // calling method will handle first event param
            return false;
        }
        EventMessage<?> eventMessage = (EventMessage<?>) args[0];
        operation.put("eventType", eventMessage.getPayloadType().getName());
        operation.put("eventId", eventMessage.getIdentifier());
        operation.put("timestamp", eventMessage.getTimestamp().toString());
        Axon20OperationUtils.addMetaDataTo(operation, eventMessage);
        return true;
    }

    /**
     * Processes the command handler arguments
     * by populating the given {@link Operation}
     *
     * @param args      The arguments of the operation
     * @param operation The operation
     * @return true if first arg was {@link CommandMessage} and has been handled,
     *         false if calling code still needs to handle it as a command.
     */
    static boolean processCommandMessage(Object[] args, Operation operation) {
        if (!(args[0] instanceof CommandMessage<?>)) {
            // calling method will handle first command param
            return false;
        }
        CommandMessage<?> commandMessage = (CommandMessage<?>) args[0];
        operation.put("commandType", commandMessage.getPayloadType().getName());
        operation.put("commandId", commandMessage.getIdentifier());
        Axon20OperationUtils.addMetaDataTo(operation, commandMessage);
        return true;
    }

    private static void addMetaDataTo(Operation operation, MetaData metaData) {
        OperationMap map = operation.createMap("metaData");
        for (Entry<String, Object> entry : metaData.entrySet()) {
            map.put(entry.getKey(), entry.getValue().toString());
        }
    }
}