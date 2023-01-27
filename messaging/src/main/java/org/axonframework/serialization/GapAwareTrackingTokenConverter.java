/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.serialization;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import org.axonframework.eventhandling.GapAwareTrackingToken;

import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Dedicated {@link CollectionConverter} implementation to de-/serializer the {@link GapAwareTrackingToken} and
 * {@code org.axonframework.eventsourcing.eventstore.GapAwareTrackingToken}.
 * <p>
 * Necessary due to the {@link java.util.concurrent.ConcurrentSkipListSet} used within the
 * {@code GapAwareTrackingToken}, as {@link XStream} is incapable to marshall / unmarshall these
 * {@link Collection Collections} from the {@code java.util.concurrent} package. In pre-JDK16 times, {@code XStream}
 * would use the {@link com.thoughtworks.xstream.converters.reflection.ReflectionConverter} for this.
 *
 * @author Steven van Beelen
 * @since 4.7.0
 */
public final class GapAwareTrackingTokenConverter extends CollectionConverter {

    private static final String GAP_AWARE_TRACKING_TOKEN =
            "org.axonframework.eventhandling.GapAwareTrackingToken";
    private static final String LEGACY_GAP_AWARE_TRACKING_TOKEN =
            "org.axonframework.eventsourcing.eventstore.GapAwareTrackingToken";

    private static final String INDEX_NODE = "index";
    private static final String GAPS_NODE = "gaps";

    private final ReflectivelyConstructedGapSetConverter reflectivelyConstructedGapSetConverter;

    /**
     * Constructs a {@link GapAwareTrackingTokenConverter}.
     *
     * @param mapper The mapper to be used by the constructed {@link GapAwareTrackingTokenConverter}.
     */
    public GapAwareTrackingTokenConverter(Mapper mapper) {
        super(mapper);
        reflectivelyConstructedGapSetConverter = new ReflectivelyConstructedGapSetConverter(mapper);
    }

    @Override
    public void marshal(Object source,
                        HierarchicalStreamWriter writer,
                        MarshallingContext context) {
        GapAwareTrackingToken token = (GapAwareTrackingToken) source;

        writer.startNode(INDEX_NODE);
        writer.setValue(String.valueOf(token.getIndex()));
        writer.endNode();

        writer.startNode(GAPS_NODE);
        SortedSet<Long> gaps = token.getGaps();
        for (Long gap : gaps) {
            writeCompleteItem(gap, context, writer);
        }
        writer.endNode();
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        // Read the index
        reader.moveDown();
        //noinspection WrapperTypeMayBePrimitive
        Long index = Long.valueOf(reader.getValue());
        reader.moveUp();

        // Read the gaps
        Set<Long> gaps;
        reader.moveDown();
        if (doesNotHaveClassAttribute(reader)) {
            //noinspection unchecked
            gaps = (TreeSet<Long>) createCollection(TreeSet.class);
            populateCollection(reader, context, gaps);
        } else {
            //noinspection unchecked
            gaps = ((TreeSet<Long>) reflectivelyConstructedGapSetConverter.unmarshal(reader, context));
        }
        reader.moveUp();
        return new GapAwareTrackingToken(index, gaps);
    }

    /**
     * Returns {@code true} if the given {@code reader} at its current position does not contain the {@code "class"}
     * attribute, and {@code false} otherwise.
     * <p>
     * Since the {@link GapAwareTrackingTokenConverter} during marshalling <b>does not</b> set any class attributes, we
     * can assume we are dealing with the marshalled format of this converter when {@code true} is returned. Otherwise,
     * we need to assume we're dealing with a marshalled format from the
     * {@link com.thoughtworks.xstream.converters.reflection.ReflectionConverter}.
     *
     * @param reader The reader used to request if the attribute {@code "class"} is present at the current position.
     * @return {@code true} if the given {@code reader} at its current position does not contain the {@code "class"}
     * attribute, and {@code false} otherwise.
     */
    private static boolean doesNotHaveClassAttribute(HierarchicalStreamReader reader) {
        return reader.getAttribute("class") == null;
    }

    @Override
    public boolean canConvert(Class type) {
        return GAP_AWARE_TRACKING_TOKEN.equals(type.getName())
                || LEGACY_GAP_AWARE_TRACKING_TOKEN.equals(type.getName());
    }

    private static final class ReflectivelyConstructedGapSetConverter extends CollectionConverter {

        // The marshalled ConcurrentSkipListSet of a GapAwareTrackingToken starts the gap set with a node named "default".
        private static final String DEFAULT_NODE = "default";
        // The marshalled ConcurrentSkipListSet of a GapAwareTrackingToken ends the gap set with a node named "null".
        private static final String NULL_NODE = "null";

        ReflectivelyConstructedGapSetConverter(Mapper mapper) {
            super(mapper);
        }

        @Override
        public boolean canConvert(Class type) {
            throw new UnsupportedOperationException(
                    "This converter is only intended to directly unmarshal "
                            + "a reflectively constructed GapAwareTrackingToken gaps collections"
            );
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            throw new UnsupportedOperationException(
                    "This converter is only intended to directly unmarshal "
                            + "a reflectively constructed GapAwareTrackingToken gaps collections"
            );
        }

        /**
         * Highly customized unmarshal method, expecting a very specific format following from the
         * {@link java.util.concurrent.ConcurrentSkipListSet} XStream marshals through it's
         * {@link com.thoughtworks.xstream.converters.reflection.ReflectionConverter}.
         * <p>
         * As we're certain we're dealing with the {@code gaps} {@link Collection} of a {@link GapAwareTrackingToken},
         * we can skip the uninteresting private fields of the {@code ConcurrentSkipListSet}.
         * <p>
         * Furthermore, as the {@code ConcurrentSkipListSet} internally uses a
         * {@link java.util.concurrent.ConcurrentSkipListMap}, we skip the values (which default to {@code booleans}.
         */
        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            //noinspection unchecked
            Set<Long> gaps = (Set<Long>) createCollection(TreeSet.class);

            reader.moveDown(); // skip <gaps class="java.util.concurrent.ConcurrentSkipListSet">
            reader.moveDown(); // skip <m class="java.util.concurrent.ConcurrentSkipListMap" serialization="custom">
            reader.moveUp(); // skip <unserializable-parents/>
            reader.moveDown(); // skip <java.util.concurrent.ConcurrentSkipListMap>

            while (reader.hasMoreChildren()) {
                reader.moveDown();
                if (readingDefaultOrNullNode(reader.getNodeName()) || readingBooleanNode(reader.getValue())) {
                    reader.moveUp();
                    continue;
                }
                gaps.add(Long.valueOf(reader.getValue()));
                reader.moveUp();
            }

            reader.moveUp();
            reader.moveUp();
            return gaps;
        }

        private static boolean readingDefaultOrNullNode(String nodeName) {
            return DEFAULT_NODE.equals(nodeName) || NULL_NODE.equals(nodeName);
        }

        private static boolean readingBooleanNode(String value) {
            return Boolean.TRUE.toString().equals(value) || Boolean.FALSE.toString().equals(value);
        }
    }
}
