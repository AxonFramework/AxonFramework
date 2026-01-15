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

import jakarta.annotation.Nonnull;
import org.axonframework.modelling.entity.EntityMetamodel;
import org.axonframework.modelling.entity.child.ChildEntityFieldDefinition;
import org.axonframework.modelling.entity.child.CommandTargetResolver;
import org.axonframework.modelling.entity.child.EntityChildMetamodel;
import org.axonframework.modelling.entity.child.EventTargetMatcher;
import org.axonframework.modelling.entity.child.SingleEntityChildMetamodel;

import java.lang.reflect.Member;

import static org.axonframework.common.ReflectionUtils.getMemberValueType;

/**
 * {@link EntityChildModelDefinition} that creates {@link EntityChildMetamodel} instances for child entities that are
 * represented as a single entity (not iterable). It resolves the child type from the member's type and creates a
 * {@link SingleEntityChildMetamodel} accordingly.
 * <p>
 * Before version 5.0.0, this class was known as the
 * {@code org.axonframework.modelling.command.inspection.AggregateMemberAnnotatedChildEntityDefinition}. The class has
 * been renamed to better fit the new entity modeling.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class SingleEntityChildModelDefinition extends AbstractEntityChildModelDefinition {

    @Override
    protected boolean isMemberTypeSupported(@Nonnull Class<?> memberType) {
        return !Iterable.class.isAssignableFrom(memberType);
    }

    @Override
    protected Class<?> getChildTypeFromMember(@Nonnull Member member) {
        return getMemberValueType(member);
    }

    @Nonnull
    @Override
    protected <C, P> EntityChildMetamodel<C, P> doCreate(
            @Nonnull Class<P> parentClass,
            @Nonnull EntityMetamodel<C> entityMetamodel,
            @Nonnull String fieldName,
            @Nonnull EventTargetMatcher<C> eventTargetMatcher,
            @Nonnull CommandTargetResolver<C> commandTargetResolver) {
        return SingleEntityChildMetamodel
                .forEntityModel(parentClass, entityMetamodel)
                .childEntityFieldDefinition(ChildEntityFieldDefinition.forFieldName(
                        parentClass, fieldName
                ))
                .commandTargetResolver(commandTargetResolver)
                .eventTargetMatcher(eventTargetMatcher)
                .build();
    }
}
