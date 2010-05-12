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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.SimpleEventBus;
import org.junit.*;

import java.security.Principal;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AuditingInterceptorTest {

    private SimpleCommandBus commandBus;
    private Principal principal;
    private StubAuditingInterceptor auditingInterceptor;

    @Before
    public void setUp() {
        AuditingContextHolder.clear();
        auditingInterceptor = new StubAuditingInterceptor();
        SimpleEventBus eventBus = new SimpleEventBus();
        auditingInterceptor.setEventBus(eventBus);
        StubCommandHandler stubCommandHandler = new StubCommandHandler(eventBus);
        SimpleCommandBus commandBus = new SimpleCommandBus();
        this.commandBus = commandBus;
        this.commandBus.setInterceptors(Arrays.asList(auditingInterceptor));
        commandBus.subscribe(String.class, stubCommandHandler);
    }

    @After
    public void tearDown() {
        AuditingContextHolder.clear();
    }

    @Test
    public void testInterceptCommand() {
        principal = new Principal() {
            @Override
            public String getName() {
                return "Axon";
            }
        };
        Object result = commandBus.dispatch("Command");
        assertEquals(1, auditingInterceptor.getLoggedContexts().size());
        AuditingContext actual = auditingInterceptor.getLoggedContexts().get(0);
        assertEquals(1, actual.getEvents().size());
        assertEquals(StubDomainEvent.class, actual.getEvents().get(0).getClass());
        assertEquals("Axon", actual.getPrincipal().getName());
        assertEquals("Command", actual.getCommand());
        assertNull("AuditingContext should be cleared after command processing",
                   AuditingContextHolder.currentAuditingContext());
        assertEquals("ok", result);
    }

    @Test
    public void testInterceptCommand_NoCurrentPrincipal() {
        Object result = commandBus.dispatch("Command");
        assertEquals(1, auditingInterceptor.getLoggedContexts().size());
        AuditingContext actual = auditingInterceptor.getLoggedContexts().get(0);
        assertEquals(1, actual.getEvents().size());
        assertEquals(StubDomainEvent.class, actual.getEvents().get(0).getClass());
        assertNull(actual.getPrincipal());
        assertEquals("ok", result);
    }

    @Test
    public void testInterceptCommand_PreviousContextIsRestored() {
        AuditingContext previousContext = new AuditingContext(null, "Previous");
        AuditingContextHolder.setContext(previousContext);
        commandBus.dispatch("Command");
        assertSame(previousContext, AuditingContextHolder.currentAuditingContext());
    }

    @Test
    public void testInterceptCommand_FailedCommandExecution() {
        try {
            commandBus.dispatch("Fail");
            fail("Expected exception");
        }
        catch (RuntimeException e) {
            assertEquals(RuntimeException.class, e.getClass());
        }
        assertEquals(0, auditingInterceptor.getLoggedContexts().size());
        assertEquals(1, auditingInterceptor.getFailedContexts().size());
    }

    private class StubAuditingInterceptor extends AuditingInterceptor {

        private List<AuditingContext> loggedContexts = new LinkedList<AuditingContext>();
        private List<AuditingContext> failedContexts = new LinkedList<AuditingContext>();

        @Override
        protected Principal getCurrentPrincipal() {
            return principal;
        }

        @Override
        protected void writeSuccessful(AuditingContext context) {
            loggedContexts.add(context);
        }

        @Override
        protected void writeFailed(AuditingContext context, Exception failureCause) {
            this.failedContexts.add(context);
        }

        public List<AuditingContext> getLoggedContexts() {
            return loggedContexts;
        }

        public List<AuditingContext> getFailedContexts() {
            return failedContexts;
        }
    }

    private static class StubCommandHandler implements CommandHandler<String> {

        private SimpleEventBus eventBus;

        public StubCommandHandler(SimpleEventBus eventBus) {
            this.eventBus = eventBus;
        }

        @Override
        public Object handle(String command) {
            if ("Fail".equals(command)) {
                throw new RuntimeException("Mock");
            }
            eventBus.publish(new StubDomainEvent());
            return "ok";
        }
    }
}
