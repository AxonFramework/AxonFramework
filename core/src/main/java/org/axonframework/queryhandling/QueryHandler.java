package org.axonframework.queryhandling;

import org.axonframework.messaging.annotation.MessageHandler;

import java.lang.annotation.*;

/**
 * Author: marc
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE})
@MessageHandler(messageType = QueryMessage.class)
public @interface QueryHandler{
    /**
     * The name of the Query this handler listens to. Defaults to the fully qualified class name of the payload type
     * (i.e. first parameter).
     *
     * @return The query name
     */
    String queryName() default "";

    /**
     * The name of the response this handler listens to. Defaults to the fully qualified class name of the response type of the operation
     *
     * @return The response name
     */
    String responseName() default "";

}
