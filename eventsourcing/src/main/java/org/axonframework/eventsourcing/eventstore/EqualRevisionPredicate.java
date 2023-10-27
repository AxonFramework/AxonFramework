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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.Serializer;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A predicate that matches against {@code DomainEventData} instances that have a revision 'equal' to the
 * revision of the class is has serialized data for. The {@link RevisionResolver} is used to resolve the revision
 * of the Class represented by the {@code DomainEventData}.
 *
 * @author Shyam Sankaran
 * @since 3.3
 */
public class EqualRevisionPredicate implements Predicate<DomainEventData<?>> {

    private final RevisionResolver resolver;
    private final Serializer serializer;

    /**
     * Initializes the Predicate with given {@code resolver} to resolve revision of the class represented by the
     * serialized data.
     *
     * @param resolver Resolver of type {@link org.axonframework.serialization.RevisionResolver } to resolve the
     *                 revision of aggregate corresponding to the snapshot
     * @param serializer The serializer used to read the payload data, in order to resolve a class
     */
    public EqualRevisionPredicate(RevisionResolver resolver, Serializer serializer) {
        this.resolver = resolver;
        this.serializer = serializer;
    }

    @Override
    public boolean test(DomainEventData<?> snapshot) {
        final SerializedType payloadType = snapshot.getPayload().getType();
        final String payloadRevision = payloadType.getRevision();
        final String aggregateRevision;
        try {
            aggregateRevision = resolver.revisionOf(serializer.classForType(payloadType));
        } catch (Exception e) {
            // Unable to resolve a revision, ignoring this snapshot
            return false;
        }
        return Objects.equals(payloadRevision, aggregateRevision);
    }
}
