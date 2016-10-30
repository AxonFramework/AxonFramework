/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.quickstart.handler;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.quickstart.api.MarkCompletedCommand;

import java.util.function.Function;

/**
 * @author Jettro Coenradie
 */
public class MarkCompletedCommandHandler implements MessageHandler<CommandMessage<?>> {

    private final Repository<ToDoItem> repository;

    public MarkCompletedCommandHandler(Repository<ToDoItem> repository) {
        this.repository = repository;
    }

    @Override
    public Object handle(CommandMessage<?> commandMessage) throws Exception {
        MarkCompletedCommand command = (MarkCompletedCommand) commandMessage.getPayload();
        Aggregate<ToDoItem> toDoItem = repository.load(command.getTodoId());
        toDoItem.execute(ToDoItem::markCompleted);
        return toDoItem.invoke(Function.identity());
    }
}
