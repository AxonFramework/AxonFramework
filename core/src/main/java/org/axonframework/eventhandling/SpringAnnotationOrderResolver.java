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

package org.axonframework.eventhandling;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

/**
 * OrderResolver implementation that checks for {@link Order @Order} annotations on the type level. When such
 * annotation is found, the value provided is used as order.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public class SpringAnnotationOrderResolver implements OrderResolver {

    @Override
    public int orderOf(EventListener listener) {
        Order order = AnnotationUtils.findAnnotation(listener.getClass(), Order.class);
        if (order == null && listener instanceof EventListenerProxy) {
            order = AnnotationUtils.findAnnotation(((EventListenerProxy) listener).getTargetType(), Order.class);
        }
        return order == null ? 0 : order.value();
    }
}
