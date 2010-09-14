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

package org.axonframework.domain;

import java.io.Serializable;

/**
 * Interface to the type that uniquely identifies an aggregate of a certain type.
 * <p/>
 * Two identifiers are considered equals when their internal (backing) values are equal, regardles of the actual
 * implementation type.
 * <p/>
 * <em>Implementation guidelines:</em><br/>Each identifier should have a String representation. Implementations are
 * REQUIRED to return this representation in the asString() method. They MAY also return this representation in the
 * toString() method.<br/>When two identifiers are compared they are equal if their String representation is equal and
 * the type of aggregate they represent is equal.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public interface AggregateIdentifier extends Serializable {

    /**
     * Returns the String representation of this aggregate identifier.
     *
     * @return the String representation of this aggregate identifier.
     */
    String asString();
}
