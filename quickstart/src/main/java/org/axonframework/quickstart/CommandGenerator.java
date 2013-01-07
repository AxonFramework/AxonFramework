/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.quickstart;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.quickstart.api.CreateToDoItemCommand;
import org.axonframework.quickstart.api.MarkCompletedCommand;

import java.util.UUID;

/**
 * Runner that uses the provided CommandGateway to send some commands to our application.
 *
 * @author Jettro Coenradie
 */
public class CommandGenerator {

    public static void sendCommands(CommandGateway commandGateway) {
        final String itemId1 = UUID.randomUUID().toString();
        final String itemId2 = UUID.randomUUID().toString();
        commandGateway.send(new CreateToDoItemCommand(itemId1, "Check if it really works!"));
        commandGateway.send(new CreateToDoItemCommand(itemId2, "Think about the next steps!"));
        commandGateway.send(new MarkCompletedCommand(itemId1));
    }
}
