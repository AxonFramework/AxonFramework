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

package org.axonframework.eventhandling;

import org.axonframework.common.annotations.Internal;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;

/**
 * Implementation of the {@link EventBus} that dispatches events in the thread the publishes them.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class SimpleEventBus extends AbstractEventBus {

    @Internal
    SimpleEventBus() {
        super();
    }

    public SimpleEventBus(UnitOfWorkFactory unitOfWorkFactory) {
        super(unitOfWorkFactory);
    }
}
