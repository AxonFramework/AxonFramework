package org.axonframework.eventhandling.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation indication that a parameter on an Event Handler method should be injected with the Timestamp of an Event
 * Message. The parameter type must be assignable from {@link org.joda.time.DateTime}.
 *
 * @author Allard Buijze
 * @since 2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Timestamp {

}
