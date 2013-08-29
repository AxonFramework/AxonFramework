package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.annotation.TargetAggregateIdentifier;
import org.axonframework.common.AxonConfigurationException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.axonframework.common.ReflectionUtils.*;

/**
 * Inspects the Annotations found on Command types to return a suitable {@link RoutingStrategy} used in
 * determining the AggregateIdentifier to be used in routing
 *
 * @author Mark Ingram
 * @since 2.1
 */
public class AnnotationRoutingResolver {

    private Class<? extends Annotation>[] candidateAnnotationTypes;

    /**
     * Initializes a resolver that solely checks for {@link TargetAggregateIdentifier} annotations
     */
    public AnnotationRoutingResolver() {
        this(TargetAggregateIdentifier.class);
    }

    /**
     * Initializes the resolver to check a variable number of candidate annotation types
     *
     * @param candidateAnnotationTypes annotation types that will be considered
     */
    public AnnotationRoutingResolver(Class<? extends Annotation>... candidateAnnotationTypes) {
        this.candidateAnnotationTypes = candidateAnnotationTypes;
    }

    /**
     * Examines the payload type to check whether any of the methods or fields declare one of the
     * candidate annotation types. Each candidate type against all methods and fields before the next.
     *
     * @param payloadType the payload type
     * @return the first instance of a matching annotated field or method if found, or null when no match is found
     */
    public RoutingStrategy getRoutingStrategy(Class<?> payloadType) {
        for(Class<? extends Annotation> aggregateIdentifierAnnotationType : candidateAnnotationTypes) {
            for (Method m : methodsOf(payloadType)) {
                if (m.isAnnotationPresent(aggregateIdentifierAnnotationType)) {
                    return new MethodValueRoutingStrategy(m);
                }
            }

            for (Field f : fieldsOf(payloadType)) {
                if (f.isAnnotationPresent(aggregateIdentifierAnnotationType)) {
                    return new FieldValueRoutingStrategy(f);
                }
            }
        }
        return null;
    }

    private static class FieldValueRoutingStrategy implements RoutingStrategy {
        private Field field;

        FieldValueRoutingStrategy(Field field) {
            this.field = field;
            ensureAccessible(field);
        }

        @Override
        public String getRoutingKey(CommandMessage<?> command) {
            Object o = getFieldValue(field, command.getPayload());
            return o == null ? null : o.toString();
        }
    }

    private static class MethodValueRoutingStrategy implements RoutingStrategy {
        private Method method;

        MethodValueRoutingStrategy(Method method) {
            this.method = method;
            ensureAccessible(method);
        }

        @Override
        public String getRoutingKey(CommandMessage<?> command) {
            try {
                Object o = method.invoke(command.getPayload());
                return o == null ? null : o.toString();
            } catch (IllegalAccessException e) {
                throw new AxonConfigurationException("The current security context does not allow extraction of "
                        + "aggregate information from the given command.", e);
            } catch (InvocationTargetException e) {
                throw new AxonConfigurationException("An exception occurred while extracting aggregate "
                        + "information form a command", e);
            }
        }
    }
}
