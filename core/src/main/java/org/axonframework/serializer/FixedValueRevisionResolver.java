package org.axonframework.serializer;

/**
 * RevisionResolver implementation that returns a fixed value as revision, regardless of the type of serialized object
 * involved. This can be useful when using the application version as Revision, for example.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class FixedValueRevisionResolver implements RevisionResolver {

    private final String revision;

    /**
     * Initializes the FixedValueRevisionResolver to always return the given <code>revision</code>, when asked.
     *
     * @param revision The revision to return
     */
    public FixedValueRevisionResolver(String revision) {
        this.revision = revision;
    }

    @Override
    public String revisionOf(Class<?> payloadType) {
        return revision;
    }
}
