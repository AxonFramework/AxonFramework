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

/**
 * Abstract implementation of the HandlerPayloadTypeResolver that uses the Void class to indicate a "null" value.
 * <code>null</code> values are not allowed on annotations.
 *
 * @param <T> The type of annotation marking the handler method
 * @author Allard Buijze
 * @since 2.1
 */
public abstract class AbstractPayloadTypeResolver<T extends Annotation> implements HandlerPayloadTypeResolver<T> {

    @Override
    public Class<?> resolvePayloadFor(T annotation) {
        Class<?> definedPayload = getDefinedPayload(annotation);
        if (definedPayload == Void.class) {
            return null;
        }
        return definedPayload;
    }

    /**
     * Returns the payload type configured on the given annotated method. A payload type of "Void" is considered to
     * represent "no explicit payload configured".
     *
     * @param annotation The annotation that defines this method to be a handler
     * @return the explicit payload type for this handler
     */
    protected abstract Class<?> getDefinedPayload(T annotation);
}
