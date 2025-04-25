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

package org.axonframework.modelling.entity.child;

import org.axonframework.common.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.StreamSupport;

/**
 * A {@link ChildEntityFieldDefinition} that uses a field to access the child entity. If getters or setters are
 * available, it will use those instead.
 *
 * @param <P> The type of the parent entity.
 * @param <F> The type of the child entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class FieldChildEntityFieldDefinition<P, F> implements ChildEntityFieldDefinition<P, F> {


    private final Field field;
    private Method optionalGetter;
    private Method optionalSetter;

    /**
     * Creates a new {@link FieldChildEntityFieldDefinition} that uses the given field to access the child entity.
     *
     * @param parentClass The class of the parent entity.
     * @param fieldName   The name of the field to use to access the child entity.
     */
    public FieldChildEntityFieldDefinition(
            Class<P> parentClass,
            String fieldName
    ) {
        this.field = StreamSupport.stream(ReflectionUtils.fieldsOf(parentClass).spliterator(), false)
                                  .filter(f -> f.getName().equals(fieldName))
                                  .findFirst()
                                  .orElseThrow(() -> {
                                      String message = String.format("Field '%s' not found in class %s",
                                                                     fieldName,
                                                                     parentClass.getName());
                                      return new IllegalArgumentException(message);
                                  });
        ReflectionUtils.ensureAccessible(field);
        detectOptionalGetter(parentClass, field);
        detectOptionalSetter(parentClass, field);
    }

    private void detectOptionalGetter(Class<P> parentClass, Field field) {
        this.optionalGetter = StreamSupport.stream(ReflectionUtils.methodsOf(parentClass).spliterator(), false)
                                           .filter(m -> m.getParameterCount() == 0)
                                           .filter(m -> m.getName().equals(field.getName())
                                                   || m.getName().equals("get" + capitalize(field.getName())))
                                           .findFirst()
                                           .orElse(null);
        if (this.optionalGetter != null) {
            ReflectionUtils.ensureAccessible(this.optionalGetter);
        }
    }

    private void detectOptionalSetter(Class<P> parentClass, Field field) {
        this.optionalSetter = StreamSupport.stream(ReflectionUtils.methodsOf(parentClass).spliterator(), false)
                                           .filter(m -> m.getParameterCount() == 1)
                                           .filter(m -> m.getName().equals(field.getName())
                                                   || m.getName().equals("set" + capitalize(field.getName()))
                                                   || m.getName().equals("evolve" + capitalize(field.getName())))
                                           .findFirst()
                                           .orElse(null);
        if (this.optionalSetter != null) {
            ReflectionUtils.ensureAccessible(this.optionalSetter);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public P evolveParentBasedOnChildEntities(P parentEntity, F result) {
        try {
            if (optionalSetter != null) {
                Object invokeResult = optionalSetter.invoke(parentEntity, result);
                if (invokeResult != null) {
                    // Is an evolve method
                    return (P) invokeResult;
                }
            }
            field.set(parentEntity, result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return parentEntity;
    }

    @SuppressWarnings("unchecked")
    @Override
    public F getChildEntities(P parentEntity) {
        try {
            if (optionalGetter != null) {
                return (F) optionalGetter.invoke(parentEntity);
            }
            return (F) field.get(parentEntity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String capitalize(String string) {
        if (string == null || string.isEmpty()) {
            return string;
        }
        return Character.toUpperCase(string.charAt(0)) + string.substring(1);
    }
}
