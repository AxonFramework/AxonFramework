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

package org.axonframework.test.aggregate;

import java.util.List;
import java.util.Map;

import org.axonframework.messaging.Message;

/**
 * Interface describing the operations available on a test fixture in the execution stage. In this stage, there is only
 * on operation: {@link #when(Object)}, which dispatches a command on this fixture's Command Bus.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public interface TestExecutor {

    /**
     * Dispatches the given command to the appropriate command handler and records all activity in the fixture for
     * result validation. If the given {@code command} is a {@link
     * org.axonframework.commandhandling.CommandMessage} instance, it will be dispatched as-is. Any other object will
     * cause the given {@code command} to be wrapped in a {@code CommandMessage} as its payload.
     *
     * @param command The command to execute
     * @return a ResultValidator that can be used to validate the resulting actions of the command execution
     */
    ResultValidator when(Object command);

    /**
     * Dispatches the given command and meta data to the appropriate command handler and records all
     * activity in the fixture for result validation. If the given {@code command} is a {@link
     * org.axonframework.commandhandling.CommandMessage} instance, it will be dispatched as-is, with given
     * additional {@code metaData}. Any other object will cause the given {@code command} to be wrapped in a
     * {@code CommandMessage} as its payload.
     *
     * @param command  The command to execute
     * @param metaData The meta data to attach to the
     * @return a ResultValidator that can be used to validate the resulting actions of the command execution
     */
    ResultValidator when(Object command, Map<String, ?> metaData);

    /**
     * Configures the given {@code domainEvents} as the "given" events. These are the events returned by the event
     * store when an aggregate is loaded.
     * <p/>
     * If an item in the given {@code domainEvents} implements {@link Message}, the
     * payload and meta data from that message are copied into a newly created Domain Event Message. Otherwise, a
     * Domain Event Message with the item as payload and empty meta data is created.
     *
     * @param domainEvents the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor andGiven(Object... domainEvents);

    /**
     * Configures the given {@code domainEvents} as the "given" events. These are the events returned by the event
     * store when an aggregate is loaded.
     * <p/>
     * If an item in the list implements {@link Message}, the payload and meta data from that
     * message are copied into a newly created Domain Event Message. Otherwise, a Domain Event Message with the item
     * as payload and empty meta data is created.
     *
     * @param domainEvents the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor andGiven(List<?> domainEvents);

    /**
     * Configures the given {@code commands} as the command that will provide the "given" events. The commands are
     * executed, and the resulting stored events are captured.
     *
     * @param commands the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor andGivenCommands(Object... commands);

    /**
     * Configures the given {@code commands} as the command that will provide the "given" events. The commands are
     * executed, and the resulting stored events are captured.
     *
     * @param commands the domain events the event store should return
     * @return a TestExecutor instance that can execute the test with this configuration
     */
    TestExecutor andGivenCommands(List<?> commands);
}
