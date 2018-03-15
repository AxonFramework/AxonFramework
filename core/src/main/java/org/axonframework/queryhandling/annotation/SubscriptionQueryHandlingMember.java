/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.queryhandling.annotation;

import java.lang.reflect.Type;

/**
 * Interface indicating that a MessageHandlingMember is capable of handling specific subscription query messages.
 *
 * @param <T> The type of entity to which the message handler will delegate the actual handling of the message
 * @author Milan Savic
 * @since 3.3
 */
public interface SubscriptionQueryHandlingMember<T> extends QueryHandlingMember<T> {

    /**
     * Gets the type of updates declared by {@link org.axonframework.queryhandling.QueryUpdateEmitter}.
     *
     * @return the type of updates
     */
    Type getUpdateType();
}
