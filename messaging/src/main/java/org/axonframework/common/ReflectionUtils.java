/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.common;

import java.lang.reflect.*;
import java.security.AccessController;
import java.util.*;

/**
 * Utility class for working with Java Reflection API.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class ReflectionUtils {

    /**
     * A map of Primitive types to their respective wrapper types.
     */
    private static final Map<Class<?>, Class<?>> primitiveWrapperTypeMap = new HashMap<>(8);

    static {
        primitiveWrapperTypeMap.put(boolean.class, Boolean.class);
        primitiveWrapperTypeMap.put(byte.class, Byte.class);
        primitiveWrapperTypeMap.put(char.class, Character.class);
        primitiveWrapperTypeMap.put(double.class, Double.class);
        primitiveWrapperTypeMap.put(float.class, Float.class);
        primitiveWrapperTypeMap.put(int.class, Integer.class);
        primitiveWrapperTypeMap.put(long.class, Long.class);
        primitiveWrapperTypeMap.put(short.class, Short.class);
    }

    /**
     * Returns the value of the given {@code field} in the given {@code object}. If necessary, the field is
     * made accessible, assuming the security manager allows it.
     *
     * @param field  The field containing the value
     * @param object The object to retrieve the field's value from
     * @return the value of the {@code field} in the {@code object}
     * @throws IllegalStateException if the field is not accessible and the security manager doesn't allow it to be
     *                               made accessible
     */
    @SuppressWarnings("unchecked")
    public static <R> R getFieldValue(Field field, Object object) {
        ensureAccessible(field);
        try {
            return (R) field.get(object);
        } catch(IllegalArgumentException | IllegalAccessException ex) {
            throw new IllegalStateException("Unable to access field for getting.", ex);
        }
    }

    /**
     * Set the {@code field} of {@code object} to a certain {@code value}. If necessary, the field is made accessible,
     * assuming the security manager allows it.
     *
     * @param field  The field to set {@code value} on
     * @param object The object to set the {@code value} on {@code field}
     * @param value  The value to set on {@code field}
     * @param <T>    The type of the {@code value}
     */
    public static <T> void setFieldValue(Field field, Object object, T value) {
        ensureAccessible(field);
        try {
            field.set(object, value);
        } catch(IllegalAccessException ex) {
            throw new IllegalStateException("Unable to access field for setting.", ex);
        }
    }

    /**
     * Returns the class on which the method with given {@code methodName} and parameters of type
     * {@code parameterTypes} is declared. The given {@code instanceClass} is the instance on which the
     * method can be called. If the method is not available on the given {@code instanceClass}, {@code null}
     * is returned.
     *
     * @param instanceClass  The class on which to look for the method
     * @param methodName     The name of the method
     * @param parameterTypes The parameter types of the method
     * @return The class on which the method is declared, or {@code null} if not found
     */
    public static Class<?> declaringClass(Class<?> instanceClass, String methodName, Class<?>... parameterTypes) {
        try {
            return instanceClass.getMethod(methodName, parameterTypes).getDeclaringClass();
        } catch(NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Indicates whether the given class implements a customized equals method. This methods returns true if the
     * declaring type of the equals method is not {@code Object}.
     *
     * @param type The type to inspect
     * @return {@code true} if the given type overrides the equals method, otherwise {@code false}
     */
    public static boolean hasEqualsMethod(Class<?> type) {
        return !Object.class.equals(declaringClass(type, "equals", Object.class));
    }

    /**
     * Indicates whether the two given objects are <em>not the same</em>, override an equals method that indicates
     * they are <em>not equal</em>, or implements {@link Comparable} which indicates the two are not equal. If this
     * method cannot safely indicate two objects are not equal, it returns
     * {@code false}.
     *
     * @param value      One of the values to compare
     * @param otherValue other value to compare
     * @return {@code true} if these objects explicitly indicate they are not equal, {@code false} otherwise.
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
     * Makes the given {@code member} accessible via reflection if it is not the case already.
     *
     * @param member The member (field, method, constructor, etc) to make accessible
     * @param <T>    The type of member to make accessible
     * @return the given {@code member}, for easier method chaining
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
     * Indicates whether the given {@code member} is accessible. It does so by checking whether the member is
     * non-final and public, or made accessible via reflection.
     *
     * @param member The member (field, method, constructor, etc) to check for accessibility
     * @return {@code true} if the member is accessible, otherwise {@code false}.
     */
    public static boolean isAccessible(AccessibleObject member) {
        return member.isAccessible() || (Member.class.isInstance(member) && isNonFinalPublicMember((Member) member));
    }

    /**
     * Checks whether the given {@code member} is public and non-final. These members do no need to be set
     * accessible using reflection.
     *
     * @param member The member to check
     * @return {@code true} if the member is public and non-final, otherwise {@code false}.
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
     * @return an {@code Iterable} providing access to all declared fields in the class hierarchy
     */
    public static Iterable<Field> fieldsOf(Class<?> clazz) {
        List<Field> fields = new LinkedList<>();
        Class<?> currentClazz = clazz;
        do {
            fields.addAll(Arrays.asList(currentClazz.getDeclaredFields()));
            currentClazz = currentClazz.getSuperclass();
        } while (currentClazz != null);
        return Collections.unmodifiableList(fields);
    }

    /**
     * Utility function which returns a {@link java.lang.reflect.Method} matching the given {@code methodName} and
     * {@code parameterTypes} in the {@code clazz}.
     *
     * @param clazz          The {@link java.lang.Class} to return a method for
     * @param methodName     A {@link java.lang.String} for the simple name of the method to return
     * @param parameterTypes An array of type {@link java.lang.Class} for all the parameters which are part  of the
     *                       {@link java.lang.reflect.Method} being searched for
     * @return a {@link java.lang.reflect.Method} object from the given {@code clazz} matching the specified
     * {@code methodName}
     * @throws NoSuchMethodException if no {@link java.lang.reflect.Method} can be found matching the {@code methodName}
     *                               in {@code clazz}
     */
    public static Method methodOf(Class<?> clazz, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return clazz.getMethod(methodName, parameterTypes);
    }

    /**
     * Returns an {@link Iterable} of all the methods declared on the given class and its super classes. The iterator
     * will always return methods declared in a subtype before returning methods declared in a super type.
     *
     * @param clazz The class to return methods for
     * @return an {@code Iterable} providing access to all declared methods in the class hierarchy
     */
    public static Iterable<Method> methodsOf(Class<?> clazz) {
        List<Method> methods = new LinkedList<>();
        Class<?> currentClazz = clazz;
        do {
            methods.addAll(Arrays.asList(currentClazz.getDeclaredMethods()));
            addMethodsOnDeclaredInterfaces(currentClazz, methods);
            currentClazz = currentClazz.getSuperclass();
        } while (currentClazz != null);
        return Collections.unmodifiableList(methods);
    }

    /**
     * Returns the boxed wrapper type for the given {@code primitiveType}.
     *
     * @param primitiveType The primitive type to return boxed wrapper type for
     * @return the boxed wrapper type for the given {@code primitiveType}
     * @throws IllegalArgumentException will be thrown instead of returning null if no wrapper class was found.
     */
    public static Class<?> resolvePrimitiveWrapperType(Class<?> primitiveType) {
        Assert.notNull(primitiveType, () -> "primitiveType may not be null");
        Assert.isTrue(primitiveType.isPrimitive(), () -> "primitiveType is not actually primitive: " + primitiveType);

        Class<?> primitiveWrapperType = primitiveWrapperTypeMap.get(primitiveType);
        Assert.notNull(primitiveWrapperType, () -> "no wrapper found for primitiveType: " + primitiveType);
        return primitiveWrapperType;
    }

    private static void addMethodsOnDeclaredInterfaces(Class<?> currentClazz, List<Method> methods) {
        for (Class iface : currentClazz.getInterfaces()) {
            methods.addAll(Arrays.asList(iface.getDeclaredMethods()));
            addMethodsOnDeclaredInterfaces(iface, methods);
        }
    }

    /**
     * Indicates whether the given field has the "transient" modifier
     *
     * @param field the field to inspect
     * @return {@code true} if the field is marked transient, otherwise {@code false
     * }
     */
    public static boolean isTransient(Field field) {
        return Modifier.isTransient(field.getModifiers());
    }

    /**
     * Resolve a generic type parameter from a field declaration
     *
     * @param field            The field to find generic parameters for
     * @param genericTypeIndex The index of the type
     * @return an optional that contains the resolved type, if found
     */
    public static Optional<Class<?>> resolveGenericType(Field field, int genericTypeIndex) {
        final Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType)
                || ((ParameterizedType) genericType).getActualTypeArguments().length <= genericTypeIndex) {
            return Optional.empty();
        }
        return Optional.of((Class<?>) ((ParameterizedType) genericType).getActualTypeArguments()[genericTypeIndex]);
    }

    private ReflectionUtils() {
        // utility class
    }
}
