/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.commandhandling;

import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.messaging.commandhandling.SimpleCommandBus;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkTestUtils;

import java.util.Collections;

/**
 * Test utilities when dealing with {@link CommandBus}.
 *
 * @author Mateusz Nowak
 */
public final class CommandBusTestUtils {

    /**
     * Creates a new instance of {@link SimpleCommandBus} configured with a simple
     * {@link UnitOfWorkTestUtils#SIMPLE_FACTORY} and an empty list of processing lifecycle handler registrars.
     *
     * @return An instance of {@link SimpleCommandBus}.
     */
    public static SimpleCommandBus aCommandBus() {
        return new SimpleCommandBus(
                UnitOfWorkTestUtils.SIMPLE_FACTORY,
                Collections.emptyList()
        );
    }

    private CommandBusTestUtils() {
        // Utility class
    }
}
