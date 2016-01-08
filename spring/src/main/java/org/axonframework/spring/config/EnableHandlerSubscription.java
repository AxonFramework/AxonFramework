package org.axonframework.spring.config;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author Allard Buijze
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(MessageHandlerSubscriberDefinitionRegistrar.class)
public @interface EnableHandlerSubscription {

    boolean subscribeCommandHandlers() default true;

    boolean subscribeEventListeners() default true;

    String eventBus() default "";

    boolean subscribeEventProcessors() default true;

}
