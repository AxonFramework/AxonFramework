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

package org.axonframework.modelling.entity.domain.development;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.entity.annotation.EntityMember;
import org.axonframework.modelling.entity.domain.development.commands.AssignMarketeer;
import org.axonframework.modelling.entity.domain.development.events.MarketeerAssigned;

public class OpenSourceProject extends Project {

    @EntityMember
    private Marketeer marketeer;

    public OpenSourceProject(String projectId, String name) {
        super(projectId, name);
    }

    @CommandHandler
    public void handle(AssignMarketeer command, EventAppender appender) {
        appender.append(new MarketeerAssigned(
                command.projectId(),
                command.email(),
                command.hubspotUsername()
        ));
    }

    @EventHandler
    public void on(MarketeerAssigned event) {
        this.marketeer = new Marketeer(event.email(), event.hubspotUsername());
    }

    public Marketeer getMarketeer() {
        return marketeer;
    }
}
