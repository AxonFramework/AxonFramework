package org.axonframework.modelling.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to specify the creation policy for a command handler. Default behavior is that command handlers
 * defined on a constructor would create a new instance of the aggregate, and command handlers defined on other methods
 * expect an existing aggregate. This annotation provides the option to define policy
 * {@code AggregateCreationPolicy.CREATE_IF_MISSING} or {@code AggregateCreationPolicy.ALWAYS} on a command handler to
 * create a new instance of the aggregate from a handler operation.
 *
 * @author Marc Gathier
 * @since 4.3
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface CreationPolicy {

    AggregateCreationPolicy value() default AggregateCreationPolicy.NEVER;
}
