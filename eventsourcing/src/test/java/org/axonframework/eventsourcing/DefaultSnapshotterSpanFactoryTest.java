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

package org.axonframework.eventsourcing;

import org.axonframework.tracing.IntermediateSpanFactoryTest;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.Test;


class DefaultSnapshotterSpanFactoryTest
        extends IntermediateSpanFactoryTest<DefaultSnapshotterSpanFactory.Builder, DefaultSnapshotterSpanFactory> {

    @Test
    void createScheduleSnapshotSpanWithDefaults() {
        test(builder -> builder,
             spanFactory -> spanFactory.createScheduleSnapshotSpan("MyAggregateType", "3728973982"),
             expectedSpan("scheduleSnapshot(MyAggregateType)", TestSpanFactory.TestSpanType.INTERNAL)
                     .expectAttribute("aggregateIdentifier", "3728973982")
        );
    }

    @Test
    void createScheduleSnapshotSpanIncludesAggregateName() {
        test(builder -> builder.aggregateTypeInSpanName(true).separateTrace(false),
             spanFactory -> spanFactory.createScheduleSnapshotSpan("MyAggregateType", "3728973982"),
             expectedSpan("scheduleSnapshot(MyAggregateType)", TestSpanFactory.TestSpanType.INTERNAL)
                     .expectAttribute("aggregateIdentifier", "3728973982")
        );
    }

    @Test
    void createScheduleSnapshotSpanDoesntIncludeAggregateName() {
        test(builder -> builder.aggregateTypeInSpanName(false).separateTrace(false),
             spanFactory -> spanFactory.createScheduleSnapshotSpan("MyAggregateType", "3728973982"),
             expectedSpan("scheduleSnapshot", TestSpanFactory.TestSpanType.INTERNAL)
                     .expectAttribute("aggregateIdentifier", "3728973982")
        );
    }

    @Test
    void createScheduleSnapshotSpanIsNotAffectedBySeparateTrace() {
        test(builder -> builder.aggregateTypeInSpanName(false).separateTrace(true),
             spanFactory -> spanFactory.createScheduleSnapshotSpan("MyAggregateType", "3728973982"),
             expectedSpan("scheduleSnapshot", TestSpanFactory.TestSpanType.INTERNAL)
                     .expectAttribute("aggregateIdentifier", "3728973982")
        );
    }

    @Test
    void createCreateSnapshotSpanWithDefaults() {
        test(builder -> builder,
             spanFactory -> spanFactory.createCreateSnapshotSpan("MyAggregateType", "3728973982"),
             expectedSpan("createSnapshot(MyAggregateType)", TestSpanFactory.TestSpanType.INTERNAL)
                     .expectAttribute("aggregateIdentifier", "3728973982")
        );
    }

    @Test
    void createCreateSnapshotSpanWithSeparateTraceAndWithoutAggregateInSpanName() {
        test(builder -> builder.aggregateTypeInSpanName(false).separateTrace(true),
             spanFactory -> spanFactory.createCreateSnapshotSpan("MyAggregateType", "3728973982"),
             expectedSpan("createSnapshot", TestSpanFactory.TestSpanType.ROOT)
                     .expectAttribute("aggregateIdentifier", "3728973982")
        );
    }

    @Test
    void createCreateSnapshotSpanWithInnerTraceAndWithoutAggregateInSpanName() {
        test(builder -> builder.aggregateTypeInSpanName(false).separateTrace(false),
             spanFactory -> spanFactory.createCreateSnapshotSpan("MyAggregateType", "3728973982"),
             expectedSpan("createSnapshot", TestSpanFactory.TestSpanType.INTERNAL)
                     .expectAttribute("aggregateIdentifier", "3728973982")
        );
    }


    @Override
    protected DefaultSnapshotterSpanFactory.Builder createBuilder(SpanFactory spanFactory) {
        return DefaultSnapshotterSpanFactory.builder().spanFactory(spanFactory);
    }

    @Override
    protected DefaultSnapshotterSpanFactory createFactoryBasedOnBuilder(DefaultSnapshotterSpanFactory.Builder builder) {
        return builder.build();
    }
}