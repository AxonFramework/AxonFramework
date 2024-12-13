package org.axonframework.springboot.autoconfig;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.spring.config.SpringConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass(SpringConfigurer.class)
@AutoConfigureAfter({
        AxonAutoConfiguration.class,
        JpaAutoConfiguration.class,
        JpaEventStoreAutoConfiguration.class,
        NoOpTransactionAutoConfiguration.class,
        TransactionAutoConfiguration.class
})
public class InterceptorAutoConfiguration {

    @ConditionalOnClass(MessageDispatchInterceptor.class)
    @Autowired
    public void commandDispatchInterceptorConfigurer(CommandGateway commandGateway, List<MessageDispatchInterceptor<? super CommandMessage<?>>> interceptors) {
        interceptors.forEach(i -> commandGateway.registerDispatchInterceptor(i));
    }

    @ConditionalOnClass(MessageHandlerInterceptor.class)
    @Autowired
    public void commandHandlerInterceptorConfigurer(CommandBus commandBus, List<MessageHandlerInterceptor<? super CommandMessage<?>>> interceptors) {
        interceptors.forEach(i -> commandBus.registerHandlerInterceptor(i));
    }

    @ConditionalOnClass(MessageHandlerInterceptor.class)
    @Autowired
    public void queryHandlerInterceptorConfigurer(QueryBus queryBus, List<MessageHandlerInterceptor<? super QueryMessage<?, ?>>> interceptors) {
        interceptors.forEach(i -> queryBus.registerHandlerInterceptor(i));
    }

    @ConditionalOnClass(MessageDispatchInterceptor.class)
    @Autowired
    public void eventDispatchInterceptorConfigurer(EventBus eventBus, List<MessageDispatchInterceptor<? super EventMessage<?>>> interceptors) {
        interceptors.forEach(i -> eventBus.registerDispatchInterceptor(i));
    }

    @ConditionalOnClass(MessageDispatchInterceptor.class)
    @Autowired
    public void queryDispatchInterceptorConfigurer(QueryBus queryBus, List<MessageDispatchInterceptor<? super QueryMessage<?, ?>>> interceptors) {
        interceptors.forEach(i -> queryBus.registerDispatchInterceptor(i));
    }
}
