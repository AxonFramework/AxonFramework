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

package org.axonframework.eventsourcing.eventstore;

/**
 * Utility class to access "well known" consistency markers, such as "origin" and "infinity".
 */
abstract class ConsistencyMarkers {

    ConsistencyMarkers() {
    }

    static class OriginConsistencyMarker implements ConsistencyMarker {

        static final OriginConsistencyMarker INSTANCE = new OriginConsistencyMarker();

        private OriginConsistencyMarker() {
        }

        @Override
        public ConsistencyMarker lowerBound(ConsistencyMarker other) {
            return this;
        }

        @Override
        public ConsistencyMarker upperBound(ConsistencyMarker other) {
            return other;
        }

        @Override
        public String toString() {
            return "ORIGIN";
        }
    }

    static class InfinityConsistencyMarker implements ConsistencyMarker {

        static final InfinityConsistencyMarker INSTANCE = new InfinityConsistencyMarker();

        private InfinityConsistencyMarker() {
        }

        @Override
        public ConsistencyMarker lowerBound(ConsistencyMarker other) {
            return other;
        }

        @Override
        public ConsistencyMarker upperBound(ConsistencyMarker other) {
            return this;
        }

        @Override
        public String toString() {
            return "INFINITY";
        }
    }
}
