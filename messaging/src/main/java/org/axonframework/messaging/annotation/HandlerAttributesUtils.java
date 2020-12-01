package org.axonframework.messaging.annotation;

import org.axonframework.common.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.HashSet;
import java.util.Set;

import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;
import static org.axonframework.common.annotation.AnnotationUtils.isAnnotatedWith;

/**
 * Utility class which can generate a {@link HandlerAttributes} object for a given {@link AnnotatedElement}. It does so
 * by validating all (meta-)annotations of the given element for the presence of the {@link HasHandlerAttributes}
 * annotation. Each found (meta-)annotation's attributes will be included in the {@code HandlerAttributes}.
 * <p>
 * This utility can be used to support annotation driven {@link MessageHandlingMember} implementations to automatically
 * generate the attributes collection.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public abstract class HandlerAttributesUtils {

    private HandlerAttributesUtils() {
        // Utility class
    }

    /**
     * Create a {@link HandlerAttributes} containing all attributes of annotations (meta-)annotated with {@link
     * HasHandlerAttributes} on the given {@code element}. Attributes will be stored based on the simple name of the
     * annotation (meta-)annotated with {@code HasHandlerAttributes}.
     *
     * @param element the {@link AnnotatedElement} to extract handler attributes for.
     * @return a {@link HandlerAttributes} containing all attributes of annotations (meta-)annotated with {@link
     * HasHandlerAttributes} on the given {@code element}
     */
    public static HandlerAttributes handlerAttributes(AnnotatedElement element) {
        HandlerAttributes result = new HandlerAttributes();
        Set<Class<? extends Annotation>> visitedAnnotations = new HashSet<>();

        for (Annotation annotation : element.getAnnotations()) {
            Set<Class<? extends Annotation>> annotatedWithHasHandlerAttributes = new HashSet<>();
            if (isAnnotatedWith(annotation.annotationType(),
                                HasHandlerAttributes.class,
                                annotatedWithHasHandlerAttributes,
                                visitedAnnotations)) {
                for (Class<? extends Annotation> handlerAnnotation : annotatedWithHasHandlerAttributes) {
                    findAnnotationAttributes(element, handlerAnnotation, AnnotationUtils.OVERRIDE_ONLY).ifPresent(
                            attributes -> result.put(handlerAnnotation.getSimpleName(), attributes)
                    );
                }
            }
        }

        return result;
    }
}
