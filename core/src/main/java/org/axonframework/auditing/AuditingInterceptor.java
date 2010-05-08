/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.auditing;

import org.axonframework.commandhandling.CommandContext;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;

import java.security.Principal;

/**
 * @author Allard Buijze
 */
public abstract class AuditingInterceptor implements CommandHandlerInterceptor {

    private final AuditLogger auditLogger;
    private final EventListener eventListener;

    public AuditingInterceptor() {
        this(new NullLogger());
    }

    public AuditingInterceptor(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
        this.eventListener = new AuditingEventListener();
    }

    @Override
    public void beforeCommandHandling(CommandContext context, CommandHandler handler) {
        AuditingContext auditingContext = new AuditingContext(getCurrentPrincipal(), context.getCommand());
        AuditingContextHolder.setContext(auditingContext);
    }

    @Override
    public void afterCommandHandling(CommandContext context, CommandHandler handler) {
        AuditingContext auditingContext = AuditingContextHolder.currentAuditingContext();
        auditLogger.log(auditingContext);
        AuditingContextHolder.clear();
    }

    protected abstract Principal getCurrentPrincipal();

    private static class AuditingEventListener implements EventListener {

        @Override
        public void handle(Event event) {
            AuditingContext auditingContext = AuditingContextHolder.currentAuditingContext();
            if (auditingContext != null) {
                auditingContext.registerEvent(event);
            }
        }
    }

    private static class NullLogger implements AuditLogger {

        @Override
        public void log(AuditingContext context) {
        }
    }

    public void setEventBus(EventBus eventBus) {
        eventBus.subscribe(eventListener);
    }

}
