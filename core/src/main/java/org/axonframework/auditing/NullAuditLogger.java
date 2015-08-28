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

package org.axonframework.auditing;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventMessage;

import java.util.List;

/**
 * An implementation of {@link org.axonframework.auditing.AuditLogger} that does nothing.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class NullAuditLogger implements AuditLogger {

    /**
     * Returns a singleton instance to this logger.
     */
    public static final NullAuditLogger INSTANCE = new NullAuditLogger();

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation does nothing.
     */
    @Override
    public void logSuccessful(CommandMessage<?> command, Object returnValue, List<EventMessage> events) {
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation does nothing.
     */
    @Override
    public void logFailed(CommandMessage<?> command, Throwable failureCause, List<EventMessage> events) {
    }
}
