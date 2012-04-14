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

import org.axonframework.common.ReflectionUtils;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "/contexts/axon-namespace-support-context.xml")
public class SpringAggregateSnapshotterIntegrationTest {

    @Autowired
    @Qualifier("inThreadsnapshotter")
    private SpringAggregateSnapshotter snapshotter;

    @SuppressWarnings({"unchecked"})
    @Test
    public void testSnapshotterKnowsAllFactories() throws NoSuchFieldException {
        Map<String, AggregateFactory<?>> snapshotters = (Map<String, AggregateFactory<?>>) ReflectionUtils
                .getFieldValue(AggregateSnapshotter.class.getDeclaredField("aggregateFactories"), snapshotter);

        assertFalse("No snapshotters found", snapshotters.isEmpty());
    }
}
