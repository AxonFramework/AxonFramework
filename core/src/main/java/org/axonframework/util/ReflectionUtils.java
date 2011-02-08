/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.security.AccessController.doPrivileged;
import static org.axonframework.util.CollectionUtils.filterByType;

/**
 * Utility class for working with Java Reflection API.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class ReflectionUtils {

    private ReflectionUtils() {
        // utility class
    }

    /**
     * Returns a collection of values contained in the fields of the given <code>instance</code> that are assignable to
     * the given <code>type</code>. If the given <code>instance</code> contains fields with Collections or Maps, the
     * contents of them are investigated as well. Collections inside these collections (e.g. a List of Maps) are not
     * evaluated.
     *
     * @param instance The instance to search the fields in
     * @param type     The type that the values in the fields must be assignable to
     * @param <T>      The type that the values in the fields must be assignable to
     * @return a collection of values contained in the fields of the given <code>instance</code>. Can be empty. Is never
     *         <code>null</code>.
     */
    public static <T> Collection<T> findFieldValuesOfType(final Object instance, final Class<T> type) {
        final Set<T> children = new HashSet<T>();
        for (Field field : fieldsOf(instance.getClass())) {
            if (type.isAssignableFrom(field.getType())) { // it's an entity!
                Object fieldValue = getFieldValue(field, instance);
                if (fieldValue != null) {
                    children.add(type.cast(fieldValue));
                }
            } else if (Iterable.class.isAssignableFrom(field.getType())) {
                // it's a collection
                Iterable<?> iterable = (Iterable<?>) getFieldValue(field, instance);
                if (iterable != null) {
                    children.addAll(filterByType(iterable, type));
                }
            } else if (Map.class.isAssignableFrom(field.getType())) {
                Map map = (Map) getFieldValue(field, instance);
                if (map != null) {
                    children.addAll(filterByType(map.keySet(), type));
                    children.addAll(filterByType(map.values(), type));
                }
            }
        }
        return children;
    }

    /**
     * Returns the value of the given <code>field</code> in the given <code>object</code>. If necessary, the field is
     * made accessible, assuming the security manager allows it.
     *
     * @param field  The field containing the value
     * @param object The object to retrieve the field's value from
     * @return the value of the <code>field</code> in the <code>object</code>
     *
     * @throws IllegalStateException if the field is not accessible and the security manager doesn't allow it to be made
     *                               accessible
     */
    public static Object getFieldValue(Field field, Object object) {
        ensureAccessible(field);
        try {
            return field.get(object);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Unable to access field.", ex);
        }
    }

    /**
     * Makes the given field accessible via reflection if it is not the case already.
     *
     * @param field The field to make accessible
     * @throws IllegalStateException if the field is not accessible and the security manager doesn't allow it to be made
     *                               accessible
     */
    public static void ensureAccessible(Field field) {
        if (!isAccessible(field)) {
            doPrivileged(new FieldAccessibilityCallback(field));
        }
    }

    /**
     * Indicates whether the given field is accessible. It does so by checking whether the field is non-final and
     * public, or made accessible via reflection.
     *
     * @param field The field to check for accessibility
     * @return <code>true</code> if the field is accessible, otherwise <code>false</code>.
     */
    public static boolean isAccessible(Field field) {
        return field.isAccessible() || (Modifier.isPublic(field.getModifiers())
                && Modifier.isPublic(field.getDeclaringClass().getModifiers())
                && !Modifier.isFinal(field.getModifiers()));
    }

    /**
     * Returns an {@link Iterable} of all the fields declared on the given class and its super classes. The iterator
     * will always return fields declared in a subtype before returning fields declared in a super type.
     *
     * @param clazz The class to return fields for
     * @return an <code>Iterable</code> providing access to all declared fields in the class hierarchy
     */
    public static Iterable<Field> fieldsOf(Class<?> clazz) {
        List<Field> fields = new LinkedList<Field>();
        Class<?> currentClazz = clazz;
        do {
            fields.addAll(Arrays.asList(currentClazz.getDeclaredFields()));
            currentClazz = currentClazz.getSuperclass();
        } while (currentClazz != null);
        return Collections.unmodifiableList(fields);
    }

    /**
     * Returns an {@link Iterable} of all the methods declared on the given class and its super classes. The iterator
     * will always return methods declared in a subtype before returning methods declared in a super type.
     *
     * @param clazz The class to return methods for
     * @return an <code>Iterable</code> providing access to all declared methods in the class hierarchy
     */
    public static Iterable<Method> methodsOf(Class<?> clazz) {
        List<Method> methods = new LinkedList<Method>();
        Class<?> currentClazz = clazz;
        do {
            methods.addAll(Arrays.asList(currentClazz.getDeclaredMethods()));
            currentClazz = currentClazz.getSuperclass();
        } while (currentClazz != null);
        return Collections.unmodifiableList(methods);
    }
}
