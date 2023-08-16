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

package org.axonframework.test.aggregate;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 *
 */
public class DeleteCommand {

    @TargetAggregateIdentifier
    private final Object aggregateIdentifier;
    private final boolean asIllegalChange;

    public DeleteCommand(Object aggregateIdentifier, boolean asIllegalChange) {
        this.aggregateIdentifier = aggregateIdentifier;
        this.asIllegalChange = asIllegalChange;
    }

    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    public boolean isAsIllegalChange() {
        return asIllegalChange;
    }
}
