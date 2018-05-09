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

package org.axonframework.deadline;

import java.io.Serializable;

/**
 * Represents a context in which the deadline is scheduled. When deadline is not met, it helps locating the right
 * component in order to handle the {@link DeadlineMessage}.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class DeadlineContext implements Serializable {

    private static final long serialVersionUID = 676245069497629132L;

    /**
     * Type of the component which scheduled the deadline.
     */
    public enum Type {
        /**
         * The deadline was scheduled from an aggregate.
         */
        AGGREGATE,

        /**
         * The deadline was scheduled from a saga.
         */
        SAGA,

        /**
         * The deadline was scheduled from a user-defined type. Do note that in this case, custom {@link
         * DeadlineTargetLoader} should be provided.
         */
        OTHER
    }

    private final String id;
    private final Type type;
    private final Class<?> targetType;

    /**
     * Initializes the context in which the deadline is scheduled.
     *
     * @param id         The identifier of entity scheduling a deadline
     * @param type       The type of entity scheduling a deadline
     * @param targetType The final delegate type to handle the deadline message if deadline is not met
     */
    public DeadlineContext(String id, Type type, Class<?> targetType) {
        this.id = id;
        this.type = type;
        this.targetType = targetType;
    }

    /**
     * Gets the identifier of entity which scheduled a deadline.
     *
     * @return the identifier of entity scheduling a deadline
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the type of entity which scheduled a deadline.
     *
     * @return the type of entity which scheduled a deadline
     */
    public Type getType() {
        return type;
    }

    /**
     * Gets the final delegate type to handle the deadline message if deadline is not met.
     *
     * @return the final delegate type to handle the deadline message if deadline is not met
     */
    public Class<?> getTargetType() {
        return targetType;
    }
}
