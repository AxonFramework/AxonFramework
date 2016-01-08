package org.axonframework.spring.config;

import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.MultiValueMap;

/**
 * @author Allard Buijze
 */
public class MessageHandlerSubscriberDefinitionRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        MultiValueMap<String, Object> attributes = metadata
                .getAllAnnotationAttributes(EnableHandlerSubscription.class.getName());

        if (Boolean.TRUE.equals(attributes.getFirst("subscribeEventProcessors"))
                || Boolean.TRUE.equals(attributes.getFirst("subscribeEventListeners"))) {
            final GenericBeanDefinition definition = new GenericBeanDefinition();
            definition.setBeanClass(EventListenerSubscriber.class);
            final Object eventBusRef = attributes.getFirst("eventBus");
            if (!"".equals(eventBusRef)) {
                definition.getPropertyValues().add("eventBus",
                                                   new RuntimeBeanReference((String) eventBusRef));
            }
            registry.registerBeanDefinition("EventListenerSubscriber", definition);
        }

        if (Boolean.TRUE.equals(attributes.getFirst("subscribeCommandHandlers"))) {
            final GenericBeanDefinition definition = new GenericBeanDefinition();
            definition.setBeanClass(CommandHandlerSubscriber.class);
            registry.registerBeanDefinition("CommandHandlerSubscriber", definition);
        }
    }
}
