/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.spring.config.xml;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventsourcing.AggregateIdentifier;

/**
 * @author Allard Buijze
 */
public class SimpleAnnotatedAggregate {

    private static final long serialVersionUID = 2918101112106880702L;

    @AggregateIdentifier
    private final String identifier;

    @CommandHandler
    public SimpleAnnotatedAggregate(CreateSimpleAggregateCommand command) {
        identifier = IdentifierFactory.getInstance().generateIdentifier();
    }

    public SimpleAnnotatedAggregate() {
        // don't ever do this in production code!!
        identifier = IdentifierFactory.getInstance().generateIdentifier();
    }

    public static class CreateSimpleAggregateCommand {
    }
}
