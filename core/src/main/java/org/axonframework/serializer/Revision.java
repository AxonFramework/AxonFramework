package org.axonframework.serializer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that attaches revision information to a Serializable object. The revision number is used by upcasters to
 * decide whether they need to process a certain serialized event. Generally, the revision number needs to be increased
 * each time the structure of an event has been changed in an incompatible manner.
 * <p/>
 * Although revision numbers are inherited, you are strictly advised to only annotate the actual implementation classes
 * used. This will make it easier to keep the necessary upcasters up-to-date.
 *
 * @author Allard Buijze
 * @since 2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface Revision {

    /**
     * The revision number for this object.
     */
    int value();
}
