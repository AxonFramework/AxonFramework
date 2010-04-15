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

package org.axonframework.test;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.domain.DomainEvent;
import org.axonframework.eventsourcing.EventSourcingRepository;

/**
 * @author Allard Buijze
 */
public interface FixtureConfiguration {

    FixtureConfiguration registerGenericRepository(Class<?> aggregateClass);

    FixtureConfiguration registerRepository(EventSourcingRepository<?> repository);

    FixtureConfiguration registerAnnotatedCommandHandler(Object annotatedCommandHandler);

    FixtureConfiguration registerCommandHandler(Class<?> commandType, CommandHandler commandHandler);

    TestExecutor given(DomainEvent... domainEvents);
}
