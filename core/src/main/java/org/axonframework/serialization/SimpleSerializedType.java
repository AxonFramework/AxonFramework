/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.serialization;

import org.axonframework.common.Assert;

import static java.lang.String.format;

/**
 * SerializedType implementation that takes its properties as constructor parameters.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SimpleSerializedType implements SerializedType {

    private final String type;
    private final String revisionId;

    /**
     * Initialize with given <code>objectType</code> and <code>revisionNumber</code>
     *
     * @param objectType     The description of the serialized object's type
     * @param revisionNumber The revision of the serialized object's type
     */
    public SimpleSerializedType(String objectType, String revisionNumber) {
        Assert.notNull(objectType, "objectType cannot be null");
        this.type = objectType;
        this.revisionId = revisionNumber;
    }

    @Override
    public String getName() {
        return type;
    }

    @Override
    public String getRevision() {
        return revisionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SimpleSerializedType that = (SimpleSerializedType) o;

        if (revisionId != null ? !revisionId.equals(that.revisionId) : that.revisionId != null) {
            return false;
        }
        if (!type.equals(that.type)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (revisionId != null ? revisionId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return format("SimpleSerializedType[%s] (revision %s)", type, revisionId);
    }
}
