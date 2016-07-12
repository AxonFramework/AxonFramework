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

public class DenyAll implements Predicate<CommandMessage<?>>, Serializable {
    public static final DenyAll INSTANCE = new DenyAll();

    DenyAll() {
    }

    @Override
    public boolean test(CommandMessage commandMessage) {
        return false;
    }

    @Override
    public Predicate<CommandMessage<?>> and(Predicate<? super CommandMessage<?>> other) {
        return this;
    }

    @Override
    public Predicate<CommandMessage<?>> negate() {
        return AcceptAll.INSTANCE;
    }

    @Override
    public Predicate<CommandMessage<?>> or(Predicate<? super CommandMessage<?>> other) {
        return (Predicate<CommandMessage<?>>) other;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && (obj instanceof DenyAll);
    }

    @Override
    public String toString() {
        return "DenyAll{}";
    }


    @Override
    public int hashCode() {
        return 13;
    }
}
