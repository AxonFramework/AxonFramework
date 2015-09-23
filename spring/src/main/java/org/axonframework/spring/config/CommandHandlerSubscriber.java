package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SupportedCommandNamesAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;

import java.util.Collection;

/**
 * @author Allard Buijze
 */
public class CommandHandlerSubscriber implements ApplicationContextAware, SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(CommandHandlerSubscriber.class);

    private ApplicationContext applicationContext;
    private boolean started;
    private Collection<CommandHandler> commandHandlers;
    private CommandBus commandBus;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void setCommandBus(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    public void setCommandHandlers(Collection<CommandHandler> commandHandlers) {
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
    public void start() {
        if (commandBus == null && !applicationContext.getBeansOfType(CommandBus.class).isEmpty()) {
            commandBus = applicationContext.getBean(CommandBus.class);
        }
        if (commandHandlers == null) {
            commandHandlers = applicationContext.getBeansOfType(CommandHandler.class).values();
        }
        for (CommandHandler commandHandler : commandHandlers) {
            if (commandHandler instanceof SupportedCommandNamesAware) {
                for (String commandName : ((SupportedCommandNamesAware) commandHandler).supportedCommandNames()) {
                    commandBus.subscribe(commandName, commandHandler);
                }
            } else {
                logger.warn("Unable to register command handler of type {}. It doesn't implement {}",
                            commandHandler.getClass().getName(), SupportedCommandNamesAware.class.getSimpleName());
            }
        }
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
