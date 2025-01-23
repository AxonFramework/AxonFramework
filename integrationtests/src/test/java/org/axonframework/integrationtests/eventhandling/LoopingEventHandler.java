/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.integrationtests.eventhandling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.integrationtests.commandhandling.LoopingChangeDoneEvent;
import org.axonframework.integrationtests.commandhandling.UpdateStubAggregateCommand;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.ProcessingContext;

public class LoopingEventHandler {

    private final CommandBus commandBus;

    public LoopingEventHandler(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @EventHandler
    public void handleLoopingEvent(LoopingChangeDoneEvent event) {
        UpdateStubAggregateCommand testPayload = new UpdateStubAggregateCommand(event.getAggregateIdentifier());
        GenericCommandMessage<UpdateStubAggregateCommand> testCommand =
                new GenericCommandMessage<>(new MessageType("command"), testPayload);

        commandBus.dispatch(testCommand, ProcessingContext.NONE);
    }
}
