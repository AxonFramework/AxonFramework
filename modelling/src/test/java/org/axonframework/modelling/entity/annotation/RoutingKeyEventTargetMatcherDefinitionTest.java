/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.modelling.entity.annotation;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.modelling.entity.child.CommandTargetResolver;
import org.axonframework.modelling.entity.child.EventTargetMatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * Test class validating the {@link RoutingKeyEventTargetMatcherDefinition}.
 *
 * @author Mitchell Herrijgers
 */
@ExtendWith(MockitoExtension.class)
class RoutingKeyEventTargetMatcherDefinitionTest {

    private final RoutingKeyEventTargetMatcherDefinition definition = new RoutingKeyEventTargetMatcherDefinition();

    @Mock
    private AnnotatedEntityMetamodel<ChildEntity> childEntityMetamodel;

    @Test
    void allowsNoRoutingKeyOnSingleValueEntityMember() throws NoSuchFieldException {
        EventTargetMatcher<ChildEntity> result = definition.createChildEntityMatcher(
                childEntityMetamodel,
                SingleChildEntity.class.getDeclaredField("child")
        );

        assertThat(result).isEqualTo(EventTargetMatcher.MATCH_ANY());
    }

    @Test
    void doesNotAllowMissingRoutingKeyOnCollectionTypeMember() {
        assertThatThrownBy(
                () -> definition.createChildEntityMatcher(
                        childEntityMetamodel,
                        ListChildEntityWithoutRoutingKey.class.getDeclaredField("child")
                ),
                "Expected AxonConfigurationException when no routing key is present on a collection type member"
        ).isInstanceOf(AxonConfigurationException.class);
    }

    @Test
    void returnsMatcherForListChildEntityWithRoutingKey() throws NoSuchFieldException {
        EventTargetMatcher<ChildEntity> result = definition.createChildEntityMatcher(
                childEntityMetamodel,
                ListChildEntityWithRoutingKey.class.getDeclaredField("child")
        );

        assertThat(result).isNotNull()
                          .isNotEqualTo(CommandTargetResolver.MATCH_ANY());
    }

    static class SingleChildEntity {

        @SuppressWarnings("unused")
        @EntityMember
        private ChildEntity child;
    }


    static class ListChildEntityWithoutRoutingKey {

        @SuppressWarnings("unused")
        @EntityMember
        private List<ChildEntity> child;
    }

    static class ListChildEntityWithRoutingKey {

        @SuppressWarnings("unused")
        @EntityMember(routingKey = "key")
        private List<ChildEntity> child;
    }

    static class ChildEntity {

        @SuppressWarnings("unused")
        String key() {
            return "key";
        }
    }
}
