/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.modelling.command.AggregateMember;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the use the {@link AnnotatedAggregateMetaModelFactory} using the {@link
 * org.axonframework.modelling.command.AggregateMember} annotation. For the time being more specifically through using
 * this annotation with a collection of members, where the type is an interface.
 *
 * @author Steven van Beelen
 */
class AnnotatedAggregateAggregateMemberMetaModelFactoryTest {

    @Test
    void interfaceAggregateMemberThrowsAxonConfigurationException() {
        assertThrows(
                AxonConfigurationException.class,
                () -> AnnotatedAggregateMetaModelFactory.inspectAggregate(TestAggregate.class)
        );
    }

    @Test
    void interfaceAggregateMemberCollectionThrowsAxonConfigurationException() {
        assertThrows(
                AxonConfigurationException.class,
                () -> AnnotatedAggregateMetaModelFactory.inspectAggregate(TestCollectionAggregate.class)
        );
    }

    @Test
    void interfaceAggregateMemberMapThrowsAxonConfigurationException() {
        assertThrows(
                AxonConfigurationException.class,
                () -> AnnotatedAggregateMetaModelFactory.inspectAggregate(TestMapAggregate.class)
        );
    }

    @SuppressWarnings("unused")
    private static class TestAggregate {

        @AggregateMember
        private AggregateMemberInterface aggregateMember;
    }

    @SuppressWarnings("unused")
    private static class TestCollectionAggregate {

        @AggregateMember
        private List<AggregateMemberInterface> aggregateMembers;
    }

    @SuppressWarnings("unused")
    private static class TestMapAggregate {

        @AggregateMember
        private Map<String, AggregateMemberInterface> aggregateMembers;
    }

    private interface AggregateMemberInterface {

        @CommandHandler
        void handle(InterfaceMemberCommand command);
    }

    @SuppressWarnings("unused")
    private static class AggregateMemberImplementationOne implements AggregateMemberInterface {

        @Override
        public void handle(InterfaceMemberCommand command) {

        }

        @CommandHandler
        public void handle(MemberOneCommand command) {

        }
    }

    @SuppressWarnings("unused")
    private static class AggregateMemberImplementationTwo implements AggregateMemberInterface {

        @Override
        public void handle(InterfaceMemberCommand command) {

        }

        @CommandHandler
        public void handle(MemberTwoCommand command) {

        }
    }

    private static class InterfaceMemberCommand {

    }

    private static class MemberOneCommand {

    }

    private static class MemberTwoCommand {

    }
}
