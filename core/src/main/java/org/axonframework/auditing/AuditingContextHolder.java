/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.auditing;

/**
 * Utility class that stores an {@link org.axonframework.auditing.AuditingContext} in a ThreadLocal.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class AuditingContextHolder {

    AuditingContextHolder() {
        // prevent instantiation
    }

    private static final ThreadLocal<AuditingContext> currentContext = new ThreadLocal<AuditingContext>();

    /**
     * Returns the AuditingContext stored for the current thread, or <code>null</code> if none was set.
     *
     * @return the AuditingContext stored for the current thread, or <code>null</code> if none was set.
     */
    public static AuditingContext currentAuditingContext() {
        return currentContext.get();
    }

    /**
     * Clears the AuditingContext stored for the current thread, if any.
     */
    static void clear() {
        currentContext.remove();
    }

    /**
     * Registers the given <code>context</code> with the current thread. If one was already present, it is overwritten
     * with the new one.
     *
     * @param context The context to store for the current context.
     */
    static void setContext(AuditingContext context) {
        currentContext.set(context);
    }
}
