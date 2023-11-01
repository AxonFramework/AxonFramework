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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.axonframework.commandhandling.distributed.commandfilter.CommandNameFilter;
import org.axonframework.messaging.GenericMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashTest {

    private ConsistentHash testSubject;
    private Member member1;
    private Member member2;

    @BeforeEach
    void setUp() {
        // md5(routingkey) -> de596bb1f674a2c001d807ce1024ae7f
        // md5(someOtherKey) -> 70ce6e22841006dd1122a89311f7e9fe
        // 276db5f995e944c0721c3daefb90f2f6 --> member3
        // 6eb9bb7d7e59cee68eff46dc71a79df4 --> member3
        // 78af45bdfdbd04abcd2509616175147d --> member2
        // becb4e793751ef9037620e28ea6f23e5 --> member1
        // e7fb11cbd06a69cf40e7a73a01deb6e6 --> member1
        // f5feff8e308a3ac329633844c75e0b25 --> member2

        member1 = new SimpleMember<>("member1", "", false, null);
        member2 = new SimpleMember<>("member2", "", false, null);
        Member member3 = new SimpleMember<>("member3", "", false, null);
        testSubject = new ConsistentHash().with(member1, 2, new CommandNameFilter("name1"))
                                          .with(member2, 2, new CommandNameFilter("name1"))
                                          .with(member3, 2, new CommandNameFilter("name3"));
    }

    @Test
    void testToString() {
        assertEquals("ConsistentHash [member1(2),member2(2),member3(2)]", testSubject.toString());
    }

    @Test
    void consistentHashChangesVersionWhenModified() {
        assertEquals(3, testSubject.version());
        assertEquals(4, testSubject.without(member1).version());
        assertEquals(4, testSubject.without(member1).without(member1).version());
    }

    @Test
    void messageRoutedToFirstEligibleMember() {
        Optional<Member> actual = testSubject.getMember("routingKey", new GenericCommandMessage<>(new GenericMessage<>("test"), "name1"));
        assertTrue(actual.isPresent());
        assertEquals("member1", actual.get().name());
    }

    @Test
    void messageRoutedToNextEligibleMemberIfFirstChoiceIsRemoved() {
        Optional<Member> actual = testSubject.without(member1).getMember("routingKey", new GenericCommandMessage<>(new GenericMessage<>("test"), "name1"));
        assertTrue(actual.isPresent());
        assertEquals("member2", actual.get().name());
    }

    @Test
    void nonEligibleMembersIgnored() {
        Optional<Member> actual = testSubject.getMember("routingKey", new GenericCommandMessage<>(new GenericMessage<>("test"), "name3"));
        assertTrue(actual.isPresent());
        assertEquals("member3", actual.get().name());
    }

    @Test
    void noMemberReturnedWhenNoEligibleMembers() {
        Optional<Member> actual = testSubject.getMember("routingKey", new GenericCommandMessage<>(new GenericMessage<>("test"), "unknown"));
        assertFalse(actual.isPresent());
    }

    @Test
    void eligibleMembersCorrectlyOrdered() {
        Collection<ConsistentHash.ConsistentHashMember> actual = testSubject.getEligibleMembers("someOtherKey");
        assertEquals(asList("member2", "member1", "member3"), actual.stream().map(ConsistentHash.ConsistentHashMember::name).collect(Collectors.toList()));
    }

    @Test
    void conflictingHashesDoNotImpactMembership() {
        ConsistentHash consistentHash = new ConsistentHash(s -> "fixed").with(member1, 1, AcceptAll.INSTANCE);
        ConsistentHash consistentHashModified = consistentHash
                                                            .with(member2, 1, AcceptAll.INSTANCE)
                                                            .without(member2);
        assertEquals(member1.name(), consistentHash.getMembers().iterator().next().name());
        assertEquals(consistentHash.getMembers(), consistentHashModified.getMembers());
    }

    @Test
    void notEqualsForModifiedInstanceWithDefaultInstance() throws Exception {
        ConsistentHash consistentHash = new ConsistentHash(s -> "fixed").with(member1, 1, AcceptAll.INSTANCE);
        assertNotEquals(consistentHash, new ConsistentHash());
    }

}
