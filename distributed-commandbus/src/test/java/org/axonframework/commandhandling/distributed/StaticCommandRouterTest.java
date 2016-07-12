/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.axonframework.commandhandling.distributed.commandfilter.DenyAll;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StaticCommandRouterTest {

    private StaticCommandRouter testSubject;
    private StaticCommandRouter.ServiceMember<String> member1;
    private StaticCommandRouter.ServiceMember<String> member2;
    private StaticCommandRouter.ServiceMember<String> member3;
    private RoutingStrategy routingStrategy;

    @Before
    public void setUp() throws Exception {
        member1 = new StaticCommandRouter.ServiceMember<>("member1", "endpoint1", 100, AcceptAll.INSTANCE);
        member2 = new StaticCommandRouter.ServiceMember<>("member2", "endpoint2", 100, AcceptAll.INSTANCE);
        member3 = new StaticCommandRouter.ServiceMember<>("member3", "endpoint3", 100, DenyAll.INSTANCE);
        routingStrategy = m -> m.getPayload().toString();
    }

    @Test
    public void testRouteToSingleMember() throws Exception {
        testSubject = new StaticCommandRouter(routingStrategy, member1);

        assertEquals("member1", testSubject.findDestination(asCommandMessage("test")).get().name());
    }

    @Test
    public void testRouteAppliesFilter() throws Exception {
        testSubject = new StaticCommandRouter(routingStrategy, member1, member3);

        for (int i = 0; i < 1000; i++) {
            assertEquals("member1", testSubject.findDestination(asCommandMessage("test"+i)).get().name());
        }
    }

    @Test
    public void testAllAvailableNodesAreSelected() throws Exception {
        testSubject = new StaticCommandRouter(routingStrategy, member1, member2, member3);
        Map<String, AtomicLong> selections = new HashMap<>();

        for (int i = 0; i < 1000; i++) {
            Member destination = testSubject.findDestination(asCommandMessage("test" + i)).get();
            selections.putIfAbsent(destination.name(), new AtomicLong(0));
            selections.get(destination.name()).incrementAndGet();
        }

        assertEquals(new HashSet<>(Arrays.asList("member1", "member2")), selections.keySet());
        assertTrue(selections.get("member1").intValue() > 400);
        assertTrue(selections.get("member2").intValue() > 400);
        assertEquals(1000, selections.get("member1").intValue() +  selections.get("member2").intValue());
    }

    @Test
    public void testGetMembers() throws Exception {
        testSubject = new StaticCommandRouter(routingStrategy, member1, member2, member3);

        assertEquals(new HashSet<>(Arrays.asList(member1, member2, member3)), testSubject.getMembers());
    }
}
