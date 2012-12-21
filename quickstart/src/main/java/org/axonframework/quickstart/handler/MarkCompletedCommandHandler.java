/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.quickstart.api.MarkCompletedCommand;
import org.axonframework.repository.Repository;
import org.axonframework.unitofwork.UnitOfWork;

/**
 * @author Jettro Coenradie
 */
public class MarkCompletedCommandHandler implements CommandHandler<MarkCompletedCommand> {

    private Repository<ToDoItem> repository;

    public MarkCompletedCommandHandler(Repository<ToDoItem> repository) {
        this.repository = repository;
    }

    @Override
    public Object handle(CommandMessage<MarkCompletedCommand> commandMessage, UnitOfWork unitOfWork) throws Throwable {
        MarkCompletedCommand command = commandMessage.getPayload();
        ToDoItem toDoItem = repository.load(command.getTodoId());
        toDoItem.markCompleted();
        return toDoItem;
    }
}
