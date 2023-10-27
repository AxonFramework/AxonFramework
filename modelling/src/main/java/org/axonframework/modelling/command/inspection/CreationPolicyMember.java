/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.modelling.command.inspection;

import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.command.AggregateCreationPolicy;

/**
 * Interface specifying a message handler containing a creation policy definition.
 *
 * @author Marc Gathier
 * @since 4.3
 */
public interface CreationPolicyMember<T> extends MessageHandlingMember<T> {

    /**
     * Returns the creation policy set on the {@link MessageHandlingMember}.
     *
     * @return the creation policy set on the handler
     */
    AggregateCreationPolicy creationPolicy();
}
