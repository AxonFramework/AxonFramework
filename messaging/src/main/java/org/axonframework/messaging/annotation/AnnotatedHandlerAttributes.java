package org.axonframework.messaging.annotation;

import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.AbstractHandlerAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;
import static org.axonframework.common.annotation.AnnotationUtils.isAnnotatedWith;

/**
 * Container for message handler attributes, constructed through inspecting an {@link AnnotatedElement}. It does so by
 * validating all (meta-)annotations of the given element for the presence of the {@link HasHandlerAttributes}
 * annotation. Each found (meta-)annotation's attributes will be included.
 * <p>
 * This implementation can discover several collections of attributes. All attributes are prefixed with the simple name
 * of the annotation (meta-)annotated with {@code HasHandlerAttributes}, separated by a dot.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public class AnnotatedHandlerAttributes extends AbstractHandlerAttributes {

    /**
     * Create an {@link AnnotatedHandlerAttributes} containing all attributes of annotations (meta-)annotated with
     * {@link HasHandlerAttributes} on the given {@code element}. Each attribute will be stored based on the simple name
     * of the annotation (meta-)annotated with {@code HasHandlerAttributes} combined with the attribute name.
     * <p>
     * The handler annotation name and attribute name are separated by dots. This leads to a key format of {@code
     * "[handlerType].[attributeName]"}.
     *
     * @param element the {@link AnnotatedElement} to extract handler attributes for
     */
    public AnnotatedHandlerAttributes(AnnotatedElement element) {
        super(constructHandlerAttributesFor(element));
    }

    private static Map<String, Object> constructHandlerAttributesFor(AnnotatedElement element) {
        final Map<String, Object> attributes = new HashMap<>();
        Set<Class<? extends Annotation>> visitedAnnotations = new HashSet<>();

        for (Annotation annotation : element.getAnnotations()) {
            Set<Class<? extends Annotation>> annotatedWithHasHandlerAttributes = new HashSet<>();
            if (isAnnotatedWith(annotation.annotationType(),
                                HasHandlerAttributes.class,
                                annotatedWithHasHandlerAttributes,
                                visitedAnnotations)) {
                for (Class<? extends Annotation> handlerAnnotation : annotatedWithHasHandlerAttributes) {
                    findAnnotationAttributes(element, handlerAnnotation, AnnotationUtils.OVERRIDE_ONLY).ifPresent(
                            annotatedAttributes -> annotatedAttributes.forEach(
                                    (attributeName, attribute) -> attributes.put(
                                            prefixedKey(handlerAnnotation.getSimpleName(), attributeName), attribute
                                    )
                            )
                    );
                }
            }
        }
        return attributes;
    }

    private static String prefixedKey(String handlerType, String attributeName) {
        return handlerType + "." + attributeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnotatedHandlerAttributes that = (AnnotatedHandlerAttributes) o;
        return Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributes);
    }

    @Override
    public String toString() {
        return "AnnotatedHandlerAttributes{" +
                "attributes=" + attributes +
                '}';
    }
}
