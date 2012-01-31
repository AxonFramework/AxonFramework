package org.axonframework.insight;

import java.util.Map.Entry;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.Message;

import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationMap;

public class Axon20OperationUtils {
    
    static void addMetaDataTo(Operation operation, Message<?> message) {
        if (message != null) {
            OperationMap map = operation.createMap("metaData");
            for (Entry<String, Object> entry: message.getMetaData().entrySet()) {
                map.put(entry.getKey(), entry.getValue().toString());
            }

        }
    }
    
    static boolean processEventMessage(Object[] args, Operation operation) {
        if (!(args[0] instanceof EventMessage<?>)) return false;
        EventMessage<?> eventMessage = (EventMessage<?>) args[0];
        operation.put("eventType", eventMessage.getPayloadType().getName());
        operation.put("eventId", eventMessage.getIdentifier());
        operation.put("timestamp", eventMessage.getTimestamp().toString());
        Axon20OperationUtils.addMetaDataTo(operation, eventMessage);
        return true;
    }

    static boolean processCommandMessage(Object[] args, Operation operation) {
        if (!(args[0] instanceof CommandMessage<?>)) return false;
        CommandMessage<?> commandMessage = (CommandMessage<?>) args[0];
        operation.put("commandType", commandMessage.getPayloadType().getName());
        operation.put("commandId", commandMessage.getIdentifier());
        Axon20OperationUtils.addMetaDataTo(operation, commandMessage);
        return true;
    }

}
