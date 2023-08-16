package org.axonframework.messaging.command;

import org.axonframework.messaging.MessageHandlingContext;

import java.util.function.Supplier;

public interface CommandHandlingContext extends MessageHandlingContext<CommandMessage<?>> {

    default Result returnSuccess() {
        return returnSuccess(() -> null);
    }

    default Result returnSuccess(Object result) {
        return returnSuccess(() -> result);
    }

    Result returnSuccess(Supplier<Object> result);

}
