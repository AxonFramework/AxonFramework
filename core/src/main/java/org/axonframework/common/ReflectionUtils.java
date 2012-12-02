/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.common;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

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
     * Returns the value of the given <code>field</code> in the given <code>object</code>. If necessary, the field is
     * made accessible, assuming the security manager allows it.
     *
     * @param field  The field containing the value
     * @param object The object to retrieve the field's value from
     * @return the value of the <code>field</code> in the <code>object</code>
     *
     * @throws IllegalStateException if the field is not accessible and the security manager doesn't allow it to be
     *                               made
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
     * Returns the class on which the method with name "<code>getter</code>" and parameters of type
     * <code>parameterTypes</code> is declared. The given <code>instanceClass</code> is the instance on which the
     * method cn be called. If the method is not available on the given <code>instanceClass</code>, <code>null</code>
     * is returned.
     *
     * @param instanceClass  The class on which to look for the method
     * @param methodName     The name of the method
     * @param parameterTypes The parameter types of the method
     * @return The class on which the method is decalred, or <code>null</code> if not found
     */
    public static Class<?> declaringClass(Class<?> instanceClass, String methodName, Class<?>... parameterTypes) {
        try {
            return instanceClass.getMethod(methodName, parameterTypes).getDeclaringClass();
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Indicates whether the given class implements a customized equals method. This methods returns true if the
     * declaring type of the equals method is not <code>Object</code>.
     *
     * @param type The type to inspect
     * @return <code>true</code> if the given type overrides the equals method, otherwise <code>false</code>
     */
    public static boolean hasEqualsMethod(Class<?> type) {
        return !Object.class.equals(declaringClass(type, "equals", Object.class));
    }

    /**
     * Indicates whether the two given objects are <em>not the same</em>, override an equals method that indicates
     * they are <em>not equal</em>, or implements {@link Comparable} which indicates the two are not equal. If this
     * method cannot safely indicate two objects are not equal, it returns
     * <code>false</code>.
     *
     * @param value      One of the values to compare
     * @param otherValue other value to compate
     * @return <code>true</code> if these objects explicitly indicate they are not equal, <code>false</code> otherwise.
     */
    @SuppressWarnings("unchecked")
    public static boolean explicitlyUnequal(Object value, Object otherValue) {
        if (value == otherValue) { // NOSONAR (The == comparison is intended here)
            return false;
        } else if (value == null || otherValue == null) {
            return true;
        } else if (value instanceof Comparable) {
            return ((Comparable) value).compareTo(otherValue) != 0;
        } else if (hasEqualsMethod(value.getClass())) {
            return !value.equals(otherValue);
        }
        return false;
    }

    /**
     * Makes the given <code>member</code> accessible via reflection if it is not the case already.
     *
     * @param member The member (field, method, constructor, etc) to make accessible
     * @param <T>    The type of member to make accessible
     * @return the given <code>member</code>, for easier method chaining
     *
     * @throws IllegalStateException if the member is not accessible and the security manager doesn't allow it to be
     *                               made accessible
     */
    public static <T extends AccessibleObject> T ensureAccessible(T member) {
        if (!isAccessible(member)) {
            AccessController.doPrivileged(new MemberAccessibilityCallback(member));
        }
        return member;
    }

    /**
     * Indicates whether the given <code>member</code> is accessible. It does so by checking whether the member is
     * non-final and public, or made accessible via reflection.
     *
     * @param member The member (field, method, constructor, etc) to check for accessibility
     * @return <code>true</code> if the member is accessible, otherwise <code>false</code>.
     */
    public static boolean isAccessible(AccessibleObject member) {
        return member.isAccessible() || (Member.class.isInstance(member) && isNonFinalPublicMember((Member) member));
    }

    /**
     * Checks whether the given <code>member</code> is public and non-final. These members do no need to be set
     * accessible using reflection.
     *
     * @param member The member to check
     * @return <code>true</code> if the member is public and non-final, otherwise <code>false</code>.
     *
     * @see #isAccessible(java.lang.reflect.AccessibleObject)
     * @see #ensureAccessible(java.lang.reflect.AccessibleObject)
     */
    public static boolean isNonFinalPublicMember(Member member) {
        return (Modifier.isPublic(member.getModifiers())
                && Modifier.isPublic(member.getDeclaringClass().getModifiers())
                && !Modifier.isFinal(member.getModifiers()));
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
