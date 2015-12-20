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

package org.axonframework.common.annotation;

import java.lang.reflect.AccessibleObject;

/**
 * Defines which members (methods, constructors, etc) are considered message handlers. A typical approach is by adding
 * an annotation on the method, but it is certainly not limited to that approach.
 *
 * @param <T> The type of member supported by this handler
 * @author Allard Buijze
 * @since 2.1
 */
public interface LegacyHandlerDefinition<T extends AccessibleObject> {

    /**
     * Indicates whether the given member is to be considered a message handler
     *
     * @param member The member to verify
     * @return <code>true</code> if the given <code>member</code> is a message handler, otherwise <code>false</code>
     */
    boolean isMessageHandler(T member);

    /**
     * Returns the explicitly defined payload of supported messages on the given <code>member</code>
     *
     * @param member the member method
     * @return the explicitly configured payload type, or <code>null</code> if the payload must be deducted from the
     * handler's parameters
     */
    Class<?> resolvePayloadFor(T member);
}
