/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.integrationtests.commandhandling;

/**
 * A command that should cause an exception to be thrown by the aggregate.
 */
public class ProblematicCommand {

    private Object aggregateId;
    private Long aggregateVersion;

    public ProblematicCommand(Object aggregateId, Long aggregateVersion) {
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
    }

    public ProblematicCommand(Object aggregateId) {
        this.aggregateId = aggregateId;
    }

    public Object getAggregateId() {
        return aggregateId;
    }

    public Long getAggregateVersion() {
        return aggregateVersion;
    }
}
