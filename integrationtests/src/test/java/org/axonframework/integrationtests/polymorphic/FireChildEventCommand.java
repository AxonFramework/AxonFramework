/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.integrationtests.polymorphic;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * The command indicating the parent aggregate to fire a {@link ChildEvent} handled by one of the two children.
 *
 * @author Milan Savic
 */
public class FireChildEventCommand {

    @TargetAggregateIdentifier
    private final String id;

    public FireChildEventCommand(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
