package org.axonframework.commandhandling.gateway;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.LinkedList;

/**
 * A stub of {@link CommandBus} that captures sent commands.
 *
 * @author Sara Pellegrini
 * @since 4.4
 */
public class CommandBusStub implements CommandBus {

    private final LinkedList<CommandMessage<?>> sent = new LinkedList<>();

    private final CommandResultMessage result;

    public CommandBusStub() {
        this(new GenericCommandResultMessage<Object>(""));
    }

    public CommandBusStub(CommandResultMessage<?> result) {
        this.result = result;
    }

    @Override
    public <C> void dispatch(CommandMessage<C> command) {
        sent.add(command);
    }

    @Override
    public <C, R> void dispatch(CommandMessage<C> command, CommandCallback<? super C, ? super R> callback) {
        sent.add(command);
        callback.onResult(command, result);
    }

    @Override
    public Registration subscribe(String commandName, MessageHandler<? super CommandMessage<?>> handler) {
        return null;
    }

    @Override
    public Registration registerDispatchInterceptor(
            MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor) {
        return null;
    }

    @Override
    public Registration registerHandlerInterceptor(
            MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor) {
        return null;
    }

    public CommandMessage<?> lastSentCommand() {
        return sent.getLast();
    }

    public int numberOfSentCommands() {
        return sent.size();
    }
}
