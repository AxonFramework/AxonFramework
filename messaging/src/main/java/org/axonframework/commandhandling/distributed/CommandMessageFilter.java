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

package org.axonframework.commandhandling.distributed;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.commandfilter.AndCommandMessageFilter;
import org.axonframework.commandhandling.distributed.commandfilter.NegateCommandMessageFilter;
import org.axonframework.commandhandling.distributed.commandfilter.OrCommandMessageFilter;

import java.io.Serializable;
import javax.annotation.Nonnull;

/**
 * Interface describing a filter that can be applied to commands to describe the type of commands supported by a node in
 * a cluster.
 *
 * @author Allard Buijze
 * @since 4.0
 */
@FunctionalInterface
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "className")
public interface CommandMessageFilter extends Serializable {

    /**
     * Indicates whether the given {@code commandMessage} matches this filter.
     *
     * @param commandMessage the message to match
     * @return {@code true} if the command matches, otherwise {@code false}
     */
    boolean matches(@Nonnull CommandMessage<?> commandMessage);

    /**
     * Returns a filter that matches when both this instance and the given {@code other} match.
     *
     * @param other the other filter to match against
     * @return a filter that matches when both this instance and the other match
     */
    default CommandMessageFilter and(@Nonnull CommandMessageFilter other) {
        return new AndCommandMessageFilter(this, other);
    }

    /**
     * Returns a filter that matches when this instance doesn't, and vice versa.
     *
     * @return a filter that negates the result of this instance
     */
    default CommandMessageFilter negate() {
        return new NegateCommandMessageFilter(this);
    }

    /**
     * Returns a filter that matches when either this instance or the given {@code other} matches.
     *
     * @param other the other filter to match against
     * @return a filter that matches when either this instance or the other matches
     */
    default CommandMessageFilter or(@Nonnull CommandMessageFilter other) {
        return new OrCommandMessageFilter(this, other);
    }
}
