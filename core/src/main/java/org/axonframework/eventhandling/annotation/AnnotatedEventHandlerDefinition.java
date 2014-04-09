/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventhandling.annotation;

import org.axonframework.common.annotation.AbstractAnnotatedHandlerDefinition;

/**
 * HandierDefinition implementation that uses the {@link org.axonframework.eventhandling.annotation.EventHandler}
 * annotations to define handler methods.
 *
 * @author Allard Buijze
 * @since 2.1
 */
final class AnnotatedEventHandlerDefinition
        extends AbstractAnnotatedHandlerDefinition<EventHandler> {

    /**
     * Returns a singleton instance of this definition.
     */
    public static final AnnotatedEventHandlerDefinition INSTANCE = new AnnotatedEventHandlerDefinition();

    private AnnotatedEventHandlerDefinition() {
        super(EventHandler.class);
    }

    @Override
    protected Class<?> getDefinedPayload(EventHandler annotation) {
        return annotation.eventType();
    }
}
