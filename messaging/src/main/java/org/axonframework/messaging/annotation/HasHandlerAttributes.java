package org.axonframework.messaging.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation indicating the meta-annotated annotation is used for {@link org.axonframework.messaging.annotation.MessageHandlingMember}s.
 * As such it carries specific attributes which are important to the {@code MessageHandlingMember} in an annotation
 * driven Axon application.
 * <p>
 * These attributes can be extracted into an {@link AnnotatedHandlerAttributes} objects through its constructor.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface HasHandlerAttributes {

}
