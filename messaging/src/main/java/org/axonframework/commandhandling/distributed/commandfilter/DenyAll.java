/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.commandhandling.distributed.commandfilter;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.distributed.CommandMessageFilter;

/**
 * A Command Message predicate that denies all CommandMessages
 *
 * @author Koen Lavooij
 */
public enum DenyAll implements CommandMessageFilter {

    /**
     * Singleton instance of the {@link DenyAll} filter
     */
    INSTANCE;

    @Override
    public boolean matches(CommandMessage<?> message) {
        return false;
    }

    @Override
    public CommandMessageFilter and(CommandMessageFilter other) {
        return this;
    }


    @Override
    public CommandMessageFilter negate() {
        return AcceptAll.INSTANCE;
    }

    @Override
    public CommandMessageFilter or(CommandMessageFilter other) {
        return other;
    }

    @Override
    public String toString() {
        return "DenyAll{}";
    }

}
