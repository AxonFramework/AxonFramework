package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.SupportedCommandNamesAware;
import org.axonframework.messaging.MessageHandler;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

import java.util.Collection;

/**
 * Registers Spring beans that implement both MessageHandler and SupportedCommandNamesAware with the command bus.
 *
 * @author Allard Buijze
 */
public class CommandHandlerSubscriber implements ApplicationContextAware, SmartLifecycle {

    private ApplicationContext applicationContext;
    private boolean started;
    private Collection<MessageHandler> commandHandlers;
    private CommandBus commandBus;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Sets the command bus to use when subscribing command handlers. If not set the command bus is taken from Spring's
     * application context.
     *
     * @param commandBus the command bus to use when subscribing handlers
     */
    public void setCommandBus(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    /**
     * Sets the command handlers to subscribe to the bus. If not set the command handlers are taken from Spring's
     * application context by scanning for beans of type {@link MessageHandler} that can handle commands.
     *
     * @param commandHandlers command handlers to subscribe to the command bus
     */
    public void setCommandHandlers(Collection<MessageHandler> commandHandlers) {
        this.commandHandlers = commandHandlers;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void start() {
        if (commandBus == null && !applicationContext.getBeansOfType(CommandBus.class).isEmpty()) {
            commandBus = applicationContext.getBean(CommandBus.class);
        }
        if (commandHandlers == null) {
            commandHandlers = applicationContext.getBeansOfType(MessageHandler.class).values();
        }
        commandHandlers.stream().filter(commandHandler -> commandHandler instanceof SupportedCommandNamesAware)
                .forEach(commandHandler -> {
                    for (String commandName : ((SupportedCommandNamesAware) commandHandler).supportedCommandNames()) {
                        commandBus.subscribe(commandName, commandHandler);
                    }
                });
        this.started = true;
    }


    @Override
    public void stop() {
        this.started = false;
    }

    @Override
    public boolean isRunning() {
        return started;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE / 2;
    }
}
