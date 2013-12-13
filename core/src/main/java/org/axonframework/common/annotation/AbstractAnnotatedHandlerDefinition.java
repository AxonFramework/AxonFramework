/*
 * Copyright (c) 2010-2013. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;

/**
 * Abstract implementation of the HandlerDefinition that uses annotations to recognise handler methods or constructors.
 * Annotations may return <code>Void.class</code> when the payload type is undefined (as <code>null</code> is not
 * allowed as a parameter value).
 *
 * @param <T> The type of annotation marking the members as handlers
 * @author Allard Buijze
 * @since 2.1
 */
public abstract class AbstractAnnotatedHandlerDefinition<T extends Annotation>
        implements HandlerDefinition<AccessibleObject> {

    private final Class<T> annotationType;

    /**
     * Initialize the Definition, using where handlers are annotated with given <code>annotationType</code>.
     *
     * @param annotationType The type of annotation that marks the handlers
     */
    protected AbstractAnnotatedHandlerDefinition(Class<T> annotationType) {
        this.annotationType = annotationType;
    }

    @Override
    public boolean isMessageHandler(AccessibleObject member) {
        return member.isAnnotationPresent(annotationType);
    }

    @Override
    public Class<?> resolvePayloadFor(AccessibleObject member) {
        T annotation = member.getAnnotation(annotationType);
        Class<?> definedPayload = null;
        if (annotation != null) {
            definedPayload = getDefinedPayload(annotation);
            if (definedPayload == Void.class) {
                return null;
            }
        }
        return definedPayload;
    }

    /**
     * Returns the payload type configured on the given annotated method. A payload type of "Void" is considered to
     * represent "no explicit payload configured" (as null values are not permitted on annotations).
     *
     * @param annotation The annotation that defines this method to be a handler
     * @return the explicit payload type for this handler
     */
    protected abstract Class<?> getDefinedPayload(T annotation);

    @Override
    public String toString() {
        return "AnnotatedHandler{" + annotationType + '}';
    }
}
