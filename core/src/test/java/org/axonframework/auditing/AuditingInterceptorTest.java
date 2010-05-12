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
import org.axonframework.domain.Event;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.SimpleEventBus;
import org.junit.*;
import org.mockito.internal.matchers.*;

import java.security.Principal;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AuditingInterceptorTest {

    private SimpleCommandBus commandBus;
    private AuditLogger mockAuditLogger;
    private Principal principal;
    private SimpleEventBus eventBus;

    @Before
    public void setUp() {
        mockAuditLogger = mock(AuditLogger.class);
        AuditingInterceptor auditingInterceptor = new StubAuditingInterceptor(mockAuditLogger);
        eventBus = new SimpleEventBus();
        auditingInterceptor.setEventBus(eventBus);
        StubCommandHandler stubCommandHandler = new StubCommandHandler(eventBus);
        SimpleCommandBus commandBus = new SimpleCommandBus();
        this.commandBus = commandBus;
        this.commandBus.setInterceptors(Arrays.asList(auditingInterceptor));
        commandBus.subscribe(String.class, stubCommandHandler);
    }

    @Test
    public void testInterceptCommand() {
        CapturingMatcher<AuditingContext> contextCapture = new CapturingMatcher<AuditingContext>();
        principal = new Principal() {
            @Override
            public String getName() {
                return "Axon";
            }
        };
        Object result = commandBus.dispatch("Command");
        verify(mockAuditLogger).log(argThat(contextCapture));
        AuditingContext actual = (AuditingContext) contextCapture.getLastValue();
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
        CapturingMatcher<AuditingContext> contextCapture = new CapturingMatcher<AuditingContext>();
        Object result = commandBus.dispatch("Command");
        verify(mockAuditLogger).log(argThat(contextCapture));
        AuditingContext actual = contextCapture.getLastValue();
        assertEquals(1, actual.getEvents().size());
        assertEquals(StubDomainEvent.class, actual.getEvents().get(0).getClass());
        assertNull(actual.getPrincipal());
        assertEquals("ok", result);
    }

    @Test
    public void testInterceptCommand_NullLogger() {
        AuditingInterceptor auditingInterceptor = new StubAuditingInterceptor();
        auditingInterceptor.setEventBus(eventBus);
        this.commandBus.setInterceptors(Arrays.asList(auditingInterceptor));
        ContextValidatingEventListener contextCheckingEventListener = new ContextValidatingEventListener();
        eventBus.subscribe(contextCheckingEventListener);
        Object result = commandBus.dispatch("Command");
        assertNotNull(result);
        contextCheckingEventListener.assertInvoked();
    }

    private class StubAuditingInterceptor extends AuditingInterceptor {

        private StubAuditingInterceptor(AuditLogger auditLogger) {
            super(auditLogger);
        }

        public StubAuditingInterceptor() {
            super();
        }

        @Override
        protected Principal getCurrentPrincipal() {
            return principal;
        }
    }

    private static class StubCommandHandler implements CommandHandler<String> {

        private SimpleEventBus eventBus;

        public StubCommandHandler(SimpleEventBus eventBus) {
            this.eventBus = eventBus;
        }

        @Override
        public Object handle(String command) {
            eventBus.publish(new StubDomainEvent());
            return "ok";
        }
    }

    private static class ContextValidatingEventListener implements EventListener {

        private boolean isInvoked;

        @Override
        public void handle(Event event) {
            isInvoked = true;
            assertNotNull(AuditingContextHolder.currentAuditingContext());
        }

        public void assertInvoked() {
            assertTrue(this.isInvoked);

        }
    }
}
