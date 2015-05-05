package org.axonframework.spring.config;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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

    boolean subscribeClusters() default true;

}
