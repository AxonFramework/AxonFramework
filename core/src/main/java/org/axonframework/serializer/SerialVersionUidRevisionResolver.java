package org.axonframework.serializer;

import java.io.ObjectStreamClass;

/**
 * RevisionResolver implementation that returns the (String representation of the) serialVersionUID of a class. If a
 * class is not serializable, it returns <code>null</code> when asked for a revision.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SerialVersionUIDRevisionResolver implements RevisionResolver {

    @Override
    public String revisionOf(Class<?> payloadType) {
        ObjectStreamClass objectStreamClass = ObjectStreamClass.lookup(payloadType);
        return objectStreamClass == null ? null : Long.toString(objectStreamClass.getSerialVersionUID());
    }
}
