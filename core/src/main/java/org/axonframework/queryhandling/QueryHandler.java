package org.axonframework.queryhandling;

import org.axonframework.messaging.annotation.MessageHandler;

import java.lang.annotation.*;

/**
 * Marker annotation to mark any method on an object as being a QueryHandler. Use the {@link
 * AnnotationQueryHandlerAdapter} to subscribe the annotated class to the command bus.

 * The annotated method's first parameter is the query handled by that method. Optionally, the query handler may
 * specify a second parameter of type {@link org.axonframework.messaging.MetaData}. The active MetaData will be
 * passed if that parameter is supplied.
 *
 * @author Marc Gathier
 * @since 3.1
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
