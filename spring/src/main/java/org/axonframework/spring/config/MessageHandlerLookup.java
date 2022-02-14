package org.axonframework.spring.config;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * BeanDefinitionRegistryPostProcessor implementation that detects beans with Axon Message handlers and registers
 * an {@link MessageHandlerConfigurer} to have these handlers registered in the Axon Configuration.
 */
public class MessageHandlerLookup implements BeanDefinitionRegistryPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MessageHandlerLookup.class);

    /**
     * Returns a list of beans found in the given {@code register} that contain a handler for the given {@code
     * messageType}
     *
     * @param messageType The type of message to find handlers for
     * @param registry    The registry to find these handlers in
     *
     * @return a list of bean names with message handlers
     */
    public static List<String> messageHandlerBeans(Class<? extends Message<?>> messageType,
                                                   ConfigurableListableBeanFactory registry) {
        List<String> found = new ArrayList<>();
        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition bd = registry.getBeanDefinition(beanName);
            if (bd.isSingleton() && !bd.isAbstract()) {
                Class<?> beanType = registry.getType(beanName);
                if (beanType != null && hasMessageHandler(messageType, beanType)) {
                    found.add(beanName);
                }
            }
        }
        return found;
    }

    private static boolean hasMessageHandler(Class<? extends Message<?>> messageType, Class<?> beanType) {
        for (Method m : ReflectionUtils.methodsOf(beanType)) {
            Optional<Map<String, Object>> attr = AnnotationUtils.findAnnotationAttributes(m, MessageHandler.class);
            if (attr.isPresent() && messageType.isAssignableFrom((Class<?>) attr.get().get("messageType"))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory registry) throws BeansException {
        for (MessageHandlerConfigurer.Type value : MessageHandlerConfigurer.Type.values()) {
            String configurerBeanName = "MessageHandlerConfigurer$$Axon$$" + value.name();
            if (registry.containsBeanDefinition(configurerBeanName)) {
                logger.info("Message handler configurer already available. Skipping configuration");
                break;
            }

            List<String> found = messageHandlerBeans(value.getMessageType(), registry);
            if (!found.isEmpty()) {
                AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(MessageHandlerConfigurer.class)
                                                                             .addConstructorArgValue(value.name())
                                                                             .addConstructorArgValue(found)
                                                                             .getBeanDefinition();

                ((BeanDefinitionRegistry) registry).registerBeanDefinition(configurerBeanName,
                                                                           beanDefinition);
            }
        }
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // no action required
    }
}
