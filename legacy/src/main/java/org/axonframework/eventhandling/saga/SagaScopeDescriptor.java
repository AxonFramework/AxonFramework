/*
 * Copyright (c) 2010-2023. Axon Framework
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.eventhandling.saga;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Implementation of the {@link org.axonframework.modelling.saga.SagaScopeDescriptor} used to bridge serialized versions
 * of this descriptor when migrating from Axon 3.x to Axon 4.x.
 *
 * @author Steven van Beelen
 * @since 4.2
 * @deprecated in favor of the {@link org.axonframework.modelling.saga.SagaScopeDescriptor}
 */
@Deprecated
public class SagaScopeDescriptor extends org.axonframework.modelling.saga.SagaScopeDescriptor {

    /**
     * Instantiate a SagaScopeDescriptor with the provided {@code type} and {@code identifier}.
     *
     * @param type       A {@link String} describing the type of the Saga
     * @param identifier An {@link Object} denoting the identifier of the Saga
     */
    @JsonCreator
    public SagaScopeDescriptor(@JsonProperty("type") String type, @JsonProperty("identifier") Object identifier) {
        super(type, identifier);
    }
}
