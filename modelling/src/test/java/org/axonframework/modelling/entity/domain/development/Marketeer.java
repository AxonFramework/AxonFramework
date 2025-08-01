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

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.entity.domain.development.commands.ChangeMarketeerHubspotUsername;
import org.axonframework.modelling.entity.domain.development.events.MarketeerHubspotUsernameChanged;

public class Marketeer {
    private final String email;
    private String hubspotUsername;


    public Marketeer(String email, String hubspotUsername) {
        this.email = email;
        this.hubspotUsername = hubspotUsername;
    }

    @CommandHandler
    public void handle(ChangeMarketeerHubspotUsername command, EventAppender appender) {
        appender.append(new MarketeerHubspotUsernameChanged(
                command.projectId(),
                command.email(),
                command.hubspotUsername()
        ));
    }

    @EventHandler
    public void on(MarketeerHubspotUsernameChanged event) {
        this.hubspotUsername = event.hubspotUsername();
    }

    public String getEmail() {
        return email;
    }

    public String getHubspotUsername() {
        return hubspotUsername;
    }
}
