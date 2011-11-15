package org.axonframework.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that indicates the parameter needs to be resolved to the value of the Message MetaData stored under the
 * given <code>key</code>. If <code>required</code>, and no such MetaData value is available, the handler will not be
 * invoked.
 *
 * @author Allard Buijze
 * @since 2.0
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface MetaData {

    /**
     * The key of the MetaData field to inject.
     */
    String key();

    /**
     * Indicates whether the MetaData must be available in order for the Message handler method to be invoked. Defaults
     * to <code>false</code>, in which case <code>null</code> is injected as parameter.
     * <p/>
     * Note that if the annotated parameter is a primitive type, the required property will always be
     * <code>true</code>.
     */
    boolean required() default false;
}
