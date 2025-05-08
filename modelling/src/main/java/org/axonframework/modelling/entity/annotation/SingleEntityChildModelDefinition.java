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

package org.axonframework.modelling.entity.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.modelling.entity.EntityModel;
import org.axonframework.modelling.entity.child.ChildEntityFieldDefinition;
import org.axonframework.modelling.entity.child.ChildEntityMatcher;
import org.axonframework.modelling.entity.child.EntityChildModel;
import org.axonframework.modelling.entity.child.SingleEntityChildModel;

import java.lang.reflect.Member;
import java.util.List;
import java.util.Optional;

import static org.axonframework.common.ReflectionUtils.getMemberValueType;

public class SingleEntityChildModelDefinition extends AbstractEntityChildModelDefinition {

    @Override
    protected boolean isMemberTypeSupported(Class<?> memberType) {
        return !List.class.isAssignableFrom(memberType);
    }

    @Override
    protected Class<?> getChildTypeFromMember(Member member) {
        return getMemberValueType(member);
    }

    @Nonnull
    @Override
    protected <C, P> EntityChildModel<C, P> doCreate(
            @Nonnull Class<P> parentClass,
            @Nonnull EntityModel<C> childModel,
            @Nonnull String fieldName,
            @Nonnull ChildEntityMatcher<C, Message<?>> eventChildEntityMatcher,
            @Nonnull ChildEntityMatcher<C, Message<?>> commandChildEntityMatcher) {
        return SingleEntityChildModel
                        .forEntityModel(parentClass, childModel)
                        .childEntityFieldDefinition(ChildEntityFieldDefinition.forFieldName(
                                parentClass, fieldName
                        ))
                        .commandTargetMatcher(commandChildEntityMatcher::matches)
                        .eventTargetMatcher(eventChildEntityMatcher::matches)
                        .build();
    }
}
