/*
 * Copyright (c) 2010-2011. Axon Framework
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

import java.util.Map;
import java.util.WeakHashMap;

import static org.axonframework.common.ReflectionUtils.declaringClass;

/**
 * Validates the structure of an object passed as Aggregate Identifier. These objects need to (properly) override
 * the <code>toString</code> method. Two equal identifiers must always produce the same <code>toString</code> values,
 * even between JVM restarts. Typically, this also means <code>equals</code> and <code>hashCode</code> need to be
 * implemented.
 * <p/>
 * For optimization purposes, this validator keeps a white-list of all aggregate types that have passed validation.
 * This
 * reduces the amount of reflection for types that have been already inspected.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class IdentifierValidator {

    private static final IdentifierValidator INSTANCE = new IdentifierValidator();
    private static final Object NULL = new Object();

    private final Map<Class<?>, Object> whiteList = new WeakHashMap<Class<?>, Object>();

    /**
     * Returns the singleton instance of the IdentifierValidator.
     *
     * @return the IdentifierValidator instance
     */
    public static IdentifierValidator getInstance() {
        return INSTANCE;
    }

    /**
     * Verifies whether the given <code>aggregateIdentifier</code> complies with the <code>toString</code> requirements
     * and throws IllegalArgumentException if that is not the case.
     * <p/>
     * If a mere boolean is required, consider using <code>getInstance().{@link
     * #isValidIdentifier(Class)}</code>.
     *
     * @param aggregateIdentifier The identifier to validate
     */
    public static void validateIdentifier(Class<?> aggregateIdentifier) {
        if (!getInstance().isValidIdentifier(aggregateIdentifier)) {
            throw new IllegalArgumentException("One of the events contains an unsuitable aggregate identifier "
                                                       + "for this EventStore implementation. See reference guide "
                                                       + "for more information. Suspected class: " +
                                                       aggregateIdentifier.getClass().getName());
        }
    }

    private IdentifierValidator() {
        // Singleton, prevent construction
    }

    /**
     * Indicates whether or not the given <code>identifierType</code> is safe to use as aggregate identifier
     *
     * @param identifierType The class of the identifier
     * @return <code>true</code> if the identifier is valid, <code>false</code> otherwise
     */
    public boolean isValidIdentifier(Class<?> identifierType) {
        if (!whiteList.containsKey(identifierType)) {
            if (Object.class.equals(declaringClass(identifierType, "toString"))) {
                return false;
            }
            whiteList.put(identifierType, NULL);
        }
        return true;
    }
}
