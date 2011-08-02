/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.contextsupport.spring;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;

/**
 * @author Allard Buijze
 */
public class SimpleAnnotatedAggregate extends AbstractAnnotatedAggregateRoot {

    @CommandHandler
    public SimpleAnnotatedAggregate(CreateSimpleAggregateCommand command) {
    }

    public SimpleAnnotatedAggregate(AggregateIdentifier identifier) {
        super(identifier);
    }

    public static class CreateSimpleAggregateCommand {
    }
}
