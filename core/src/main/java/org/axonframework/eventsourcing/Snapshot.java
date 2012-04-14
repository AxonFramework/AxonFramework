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

package org.axonframework.eventsourcing;

import org.axonframework.domain.DomainEventMessage;

/**
 * Interface indicating that the implemented DomainEventMessage represents a Snapshot of an aggregate at a specific
 * moment in time. The payload of the snapshot
 *
 * @param <T> The type of payload contained in this Message
 * @author Allard Buijze
 * @since 2.0
 */
public interface Snapshot<T> extends DomainEventMessage<T> {

}
