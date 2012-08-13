package org.axonframework.serializer;

/**
 * Interface towards a mechanism that resolves the revision of a given payload type. Based on this revision, a
 * component is able to recognize whether a serialized version of the payload is compatible with the
 * currently known version of the payload.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface RevisionResolver {

    /**
     * Returns the revision for the given <code>payloadType</code>.
     * <p/>
     * The revision is used by upcasters to decide whether they need to process a certain serialized event.
     * Generally, the revision needs to be modified each time the structure of an event has been changed in an
     * incompatible manner.
     *
     * @param payloadType The type for which to return the revision
     * @return the revision for the given <code>payloadType</code>
     */
    String revisionOf(Class<?> payloadType);
}
