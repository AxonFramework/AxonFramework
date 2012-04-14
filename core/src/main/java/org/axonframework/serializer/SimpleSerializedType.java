package org.axonframework.serializer;

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
     * @param revisionNumber The revision number of the serialized object's type
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
