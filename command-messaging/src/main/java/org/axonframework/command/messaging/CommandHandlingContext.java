package org.axonframework.command.messaging;

import org.axonframework.messaging.MessageHandlingContext;
import org.axonframework.messaging.Result;

import java.util.function.Supplier;

public interface CommandHandlingContext extends MessageHandlingContext<CommandMessage> {

    default Result returnSuccess() {
        return returnSuccess(() -> null);
    }

    default Result returnSuccess(Object result) {
        return returnSuccess(() -> result);
    }

    Result returnSuccess(Supplier<Object> result);

}
