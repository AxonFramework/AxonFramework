package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.queryhandling.QueryMessage;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.List;

/**
 * An implementation of a Configurer Module that will register a list of beans as handlers for a specific type of
 * message.
 * <p>
 * The beans will be lazily resolved to avoid circular dependencies if any these beans relies on the Axon Configuration
 * to be available in the application context.
 * <p>
 * Typically, an application context would have an instance of this class registered for each type of message to
 * register.
 */
public class MessageHandlerConfigurer implements ConfigurerModule, ApplicationContextAware {

    private final Type type;
    private final List<String> handlerBeans;
    private ApplicationContext applicationContext;

    /**
     * Registers the beans identified in given {@code beanRefs} as the given {@code type} of handler with the
     * Axon Configurer.
     *
     * @param type     The type of handler to register the beans as
     * @param beanRefs A list of bean identifiers to register
     */
    public MessageHandlerConfigurer(Type type, List<String> beanRefs) {
        this.type = type;
        this.handlerBeans = beanRefs;
    }

    @Override
    public void configureModule(Configurer configurer) {
        switch (type) {
            case EVENT:
                handlerBeans.forEach(handler -> configurer.registerEventHandler(c -> applicationContext.getBean(handler)));
                break;
            case QUERY:
                handlerBeans.forEach(handler -> configurer.registerQueryHandler(c -> applicationContext.getBean(handler)));
                break;
            case COMMAND:
                handlerBeans.forEach(handler -> configurer.registerCommandHandler(c -> applicationContext.getBean(handler)));
                break;
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public enum Type {
        COMMAND(CommandMessage.class),
        EVENT(EventMessage.class),
        QUERY(QueryMessage.class);

        private final Class<? extends Message<?>> messageType;

        Type(Class<? extends Message> messageType) {
            this.messageType = (Class<? extends Message<?>>) messageType;
        }

        public Class<? extends Message<?>> getMessageType() {
            return messageType;
        }
    }

}
