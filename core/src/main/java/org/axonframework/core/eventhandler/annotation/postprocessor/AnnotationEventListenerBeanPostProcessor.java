/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.core.eventhandler.annotation.postprocessor;

import org.axonframework.core.eventhandler.annotation.AnnotationEventListenerAdapter;

/**
 * Spring Bean post processor that automatically generates an adapter for each bean containing {@link
 * org.axonframework.core.eventhandler.annotation.EventHandler} annotated methods.
 * <p/>
 * The beans processed by this bean post processor will handle events in the thread that delivers them. This makes this
 * post processor useful in the case of tests or when the dispatching mechanism takes care of asynchronous event
 * delivery, such as with the {@link org.axonframework.core.eventhandler.AsyncEventBus}.
 *
 * @author Allard Buijze
 * @see org.axonframework.core.eventhandler.AsyncEventBus
 * @since 0.3
 */
public class AnnotationEventListenerBeanPostProcessor extends BaseAnnotationEventListenerBeanPostProcessor {

    /**
     * {@inheritDoc}
     */
    @Override
    protected AnnotationEventListenerAdapter adapt(Object bean) {
        return new AnnotationEventListenerAdapter(bean);
    }
}
