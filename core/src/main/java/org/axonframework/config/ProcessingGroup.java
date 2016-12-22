package org.axonframework.config;

import java.lang.annotation.*;

/**
 * Hint for the Configuration API that the annotated Event Handler object should be assigned to an Event Processor with
 * the specified name.
 * <p>
 * Explicitly provided assignment rules set in the configuration will overrule hints provided by this annotation.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface ProcessingGroup {

    /**
     * The name of the Event Processor to assign the annotated Event Handler object to.
     *
     * @return the name of the Event Processor to assign objects of this type to
     */
    String value();
}
