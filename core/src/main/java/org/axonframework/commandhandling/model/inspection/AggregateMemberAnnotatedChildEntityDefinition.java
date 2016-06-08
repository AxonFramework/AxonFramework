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

package org.axonframework.commandhandling.model.inspection;

import org.axonframework.commandhandling.model.AggregateMember;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

public class AggregateMemberAnnotatedChildEntityDefinition implements ChildEntityDefinition {

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<ChildEntity<T>> createChildDefinition(Field field, EntityModel<T> declaringEntity) {
        Map<String, Object> attributes = AnnotationUtils.findAnnotationAttributes(field, AggregateMember.class).orElse(null);
        if (attributes == null
                || Iterable.class.isAssignableFrom(field.getType())
                || Map.class.isAssignableFrom(field.getType())) {
            return Optional.empty();
        }

        EntityModel entityModel = declaringEntity.modelOf(field.getType());
        String parentRoutingKey = declaringEntity.routingKey();
        return Optional.of(new AnnotatedChildEntity<>(parentRoutingKey, field, entityModel,
                                                      (Boolean) attributes.get("forwardCommands"),
                                                      (Boolean) attributes.get("forwardEvents"),
                                                      (msg, parent) -> ReflectionUtils.getFieldValue(field, parent)));
    }

}
