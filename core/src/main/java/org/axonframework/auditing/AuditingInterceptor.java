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
import org.axonframework.commandhandling.CommandHandlerInterceptor;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;

import java.security.Principal;

/**
 * Interceptor that keeps track of commands and the events that were dispatched as a result of handling that command.
 * For each incoming command, an {@link AuditingContext} is created and maintained. After command handling, the
 * implementation of this interceptor may log the information contained in the context.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class AuditingInterceptor implements CommandHandlerInterceptor {

    private final EventListener eventListener = new AuditingEventListener();

    @Override
    public Object handle(CommandContext context, InterceptorChain chain) throws Throwable {
        AuditingContext existingAuditingContext = AuditingContextHolder.currentAuditingContext();
        AuditingContext auditingContext;
        if (existingAuditingContext != null) {
            auditingContext = new AuditingContext(getCurrentPrincipal(),
                                                  existingAuditingContext.getCorrelationId(),
                                                  context.getCommand());
        } else {
            auditingContext = new AuditingContext(getCurrentPrincipal(), context.getCommand());
        }
        AuditingContextHolder.setContext(auditingContext);

        try {
            Object returnValue = chain.proceed(context);
            writeSuccessful(auditingContext);
            return returnValue;
        } catch (Throwable t) {
            writeFailed(auditingContext, t);
            throw t;
        } finally {
            if (existingAuditingContext != null) {
                AuditingContextHolder.setContext(existingAuditingContext);
            } else {
                AuditingContextHolder.clear();
            }
        }

    }

    /**
     * Returns the {@link Principal} object of the principal running this thread, or <code>null</code> if none is
     * available. Typically this Principal is provided by a security framework or ThreadLocal instance.
     *
     * @return the {@link Principal} object of the principal running this thread, or <code>null</code> if none is
     *         available
     */
    protected abstract Principal getCurrentPrincipal();

    /**
     * Called when the command handling executed successfully.
     *
     * @param context the auditing context containing the command, related events and current principal.
     */
    protected abstract void writeSuccessful(AuditingContext context);

    /**
     * Called when the command handling execution failed. Subclasses may add behavior by implementing this method. The
     * default implementation does nothing.
     *
     * @param context      the auditing context containing the command, related events and current principal.
     * @param failureCause The exception that was raised
     */
    protected void writeFailed(AuditingContext context, Throwable failureCause) {
    }

    private static class AuditingEventListener implements EventListener {

        @Override
        public void handle(Event event) {
            AuditingContext auditingContext = AuditingContextHolder.currentAuditingContext();
            if (auditingContext != null) {
                auditingContext.registerEvent(event);
            }
        }
    }

    /**
     * Configures the event bus that the interceptor will check events on. These events are assigned to their respective
     * auditing context.
     *
     * @param eventBus The event bus dispatching the events
     */
    public void setEventBus(EventBus eventBus) {
        eventBus.subscribe(eventListener);
    }

}
