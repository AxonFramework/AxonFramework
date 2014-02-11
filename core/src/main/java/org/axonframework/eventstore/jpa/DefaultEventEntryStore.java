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

package org.axonframework.eventstore.jpa;

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.serializer.SerializedObject;

/**
 * Implementation of the EventEntryStore that stores events in DomainEventEntry
 * entities and snapshot events in SnapshotEventEntry entities.
 * <p/>
 * This implementation requires that the aforementioned instances are available
 * in the current persistence context.
 * <p/>
 * <em>Note: the SerializedType of Message Meta Data is not stored in this EventEntryStore. Upon retrieval,
 * it is set to the default value (name = "org.axonframework.domain.MetaData", revision = null). See {@link
 * org.axonframework.serializer.SerializedMetaData#isSerializedMetaData(org.axonframework.serializer.SerializedObject)}</em>
 * 
 * @author Allard Buijze
 * @since 1.2
 */
public class DefaultEventEntryStore extends AbstractEntityCustomizableEventEntryStore {
	@Override
	protected DomainEventEntry aDomainEventEntryWith(String aggregateType,
			DomainEventMessage event, SerializedObject serializedPayload,
			SerializedObject serializedMetaData) {
		return new DomainEventEntry(aggregateType, event, serializedPayload,
				serializedMetaData);
	}

	@Override
	protected String domainEventEntryEntityName() {
		return DomainEventEntry.class.getSimpleName();
	}

	@Override
	protected SnapshotEventEntry aSnapshotEventEntryWith(String aggregateType,
			DomainEventMessage snapshotEvent,
			SerializedObject serializedPayload,
			SerializedObject serializedMetaData) {
		return new SnapshotEventEntry(aggregateType, snapshotEvent,
				serializedPayload, serializedMetaData);
	}

	@Override
	protected String snapshotEventEntryEntityName() {
		return SnapshotEventEntry.class.getSimpleName();
	}

}
