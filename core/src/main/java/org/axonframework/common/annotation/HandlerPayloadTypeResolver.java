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
 * Resolves the explicitly defined payload on a handler, if present.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public interface HandlerPayloadTypeResolver<T extends Annotation> {

    /**
     * Returns the explicitly defined payload of supported messages on a handler annotated with given
     * <code>annotation</code>.
     *
     * @param annotation The annotation on the handler
     * @return the explicitly configured payload type, or <code>null</code> if the payload must be deducted from the
     *         handler's parameters
     */
    Class<?> resolvePayloadFor(T annotation);
}
