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

package org.axonframework.eventsourcing.snapshotting;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.DomainEventData;

import java.util.Objects;

import static org.axonframework.common.BuilderUtils.assertNonBlank;
import static org.axonframework.common.BuilderUtils.assertNonEmpty;

/**
 * A {@link SnapshotFilter} implementation which based on a configurable allowed revision will only {@link
 * #allow(DomainEventData)} {@link DomainEventData} containing that revision. True will also be returned if the {@link
 * DomainEventData#getType()} does not match the given {@code type}, as in compliance with the {@code SnapshotFilter}
 * documentation.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public class RevisionSnapshotFilter implements SnapshotFilter {

    private final String type;
    private final String revision;

    /**
     * Instantiate a Builder to be able to create a {@link RevisionSnapshotFilter}.
     * <p>
     * The {@code type} is <b>hard requirements</b> and as such should be provided.
     * The {@code revision} should not be an empty String
     *
     * @return a Builder to be able to create a {@link RevisionSnapshotFilter}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link RevisionSnapshotFilter} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@code type} is not {@code null} or an empty String and the {@code revision} is not an empty String and will throw an {@link
     * AxonConfigurationException} if this is the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link RevisionSnapshotFilter} instance
     */
    protected RevisionSnapshotFilter(Builder builder) {
        builder.validate();
        this.type = builder.type;
        this.revision = builder.revision;
    }

    @Override
    public boolean test(DomainEventData<?> domainEventData) {
        String type = domainEventData.getType();
        String revision = domainEventData.getPayload().getType().getRevision();
        if (!Objects.equals(type, this.type)) {
            return true;
        }
        return Objects.equals(revision, this.revision);
    }

    /**
     * Builder class to instantiate a {@link RevisionSnapshotFilter}.
     * <p>
     * The {@code type} and {@code revision} are <b>hard requirements</b> and as such should be provided.
     */
    public static class Builder {

        private String type;
        private String revision;

        /**
         * Sets the aggregate {@code type} this {@link SnapshotFilter} will allow using the outcome of {@link
         * Class#getName()} on the given {@code type}. Note that if the {@code type} does not match the {@link
         * DomainEventData#getType()}, the filter will return {@code true} as per the {@code SnapshotFilter}
         * documentation.
         *
         * @param type defines aggregate type this {@link SnapshotFilter} allows
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder type(Class<?> type) {
            return type(type.getName());
        }

        /**
         * Sets the aggregate {@code type} this {@link SnapshotFilter} will allow. Note that if the {@code type} does
         * not match the {@link DomainEventData#getType()}, the filter will return {@code true} as per the {@code
         * SnapshotFilter} documentation.
         *
         * @param type defines aggregate type this {@link SnapshotFilter} allows
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder type(String type) {
            assertNonEmpty(type, "The type may not be null or empty");
            this.type = type;
            return this;
        }

        /**
         * Sets an aggregate {@code revision} this {@link SnapshotFilter} will allow.
         *
         * @param revision the aggregate
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder revision(String revision) {
            assertNonBlank(revision, "The revision may not be an empty String");
            this.revision = revision;
            return this;
        }

        /**
         * Initializes a {@link RevisionSnapshotFilter} as specified through this Builder.
         *
         * @return a {@link RevisionSnapshotFilter} as specified through this Builder
         */
        public RevisionSnapshotFilter build() {
            return new RevisionSnapshotFilter(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonEmpty(type, "The type is a hard requirement and should be provided");
            assertNonBlank(revision, "The revision should not be an empty String");
        }
    }
}
