package org.axonframework.serializer;

/**
 * Revision Resolver implementation that checks for the presence of an {@link Revision @Revision} annotation. The value
 * of that annotation is returns as the revision of the payload it annotates. Note that <code>@Revision</code> is an
 * inherited annotation, meaning that subclasses of annotated classes inherit the revision of their parent.
 * <p/>
 * This implementation returns <code>null</code> for objects that do not have a <code>@Revision</code> annotation.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class AnnotationRevisionResolver implements RevisionResolver {

    @Override
    public String revisionOf(Class<?> payloadType) {
        Revision revision = payloadType.getAnnotation(Revision.class);
        return revision != null ? revision.value() : null;
    }
}
