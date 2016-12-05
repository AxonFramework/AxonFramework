/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.distributed.commandfilter;

import org.axonframework.commandhandling.CommandMessage;

import java.io.Serializable;
import java.util.function.Predicate;

/**
 * A command filter that accepts all CommandMessages
 *
 * @author Koen Lavooij
 */
public enum AcceptAll implements Predicate<CommandMessage<?>>, Serializable {

    /**
     * Singleton instance of the {@link AcceptAll} filter
     */
    INSTANCE;

    @Override
    public boolean test(CommandMessage commandMessage) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Predicate<CommandMessage<?>> and(Predicate<? super CommandMessage<?>> other) {
        return (Predicate<CommandMessage<?>>) other;
    }

    @Override
    public Predicate<CommandMessage<?>> negate() {
        return DenyAll.INSTANCE;
    }

    @Override
    public Predicate<CommandMessage<?>> or(Predicate<? super CommandMessage<?>> other) {
        return this;
    }

    @Override
    public String toString() {
        return "AcceptAll{}";
    }
}
