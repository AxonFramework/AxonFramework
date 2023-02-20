/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.deadline.jobrunr;

import org.axonframework.common.digest.Digester;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.serialization.Serializer;

import javax.annotation.Nonnull;

/**
 * Utility to create labels for use with JobRunr. As lbels have a maximum length of 44, we need to use a hash if it
 * would go over the maximum length.
 */
public abstract class LabelUtils {

    public static final int MAX_LENGTH = 44;

    private LabelUtils() {
        //prevent instantiation
    }

    /**
     * Makes sure the returned value has a length equal or the same as the maximum size.
     *
     * @param input the input to get a valid label for
     * @return a {@link String} which can be used as a label
     */
    public static String getLabel(String input) {
        if (input.length() <= MAX_LENGTH) {
            return input;
        } else {
            return Digester.md5Hex(input);
        }
    }

    /**
     * Creates a label from a scope, by using the serializer.
     *
     * @param serializer a {@link Serializer} to serialize the provided {@code scope}
     * @param scope      a {@link ScopeDescriptor} of which a label is needed
     * @return a {@link String} which can be used as a label
     */
    public static String getScopeLabel(@Nonnull Serializer serializer, @Nonnull ScopeDescriptor scope) {
        return getLabel(serializer.serialize(scope, String.class).getData());
    }

    /**
     * Creates a label from a scope and a deadlineName, by using the serializer.
     *
     * @param serializer   a {@link Serializer} to serialize the provided {@code scope}
     * @param deadlineName the {@link String} that holds the deadline name.
     * @param scope        scope a {@link ScopeDescriptor} of which a label is needed
     * @return a {@link String} which can be used as a label
     */
    public static String getCombinedLabel(@Nonnull Serializer serializer, @Nonnull String deadlineName,
                                          @Nonnull ScopeDescriptor scope) {
        String scopeLabel = getScopeLabel(serializer, scope);
        return getLabel(deadlineName + scopeLabel);
    }
}
