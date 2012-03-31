package org.axonframework.upcasting;

import java.util.List;

/**
 * Interface indicating that the implementing mechanism is aware of Upcasters. Upcasting is the process where
 * deprecated Domain Objects (typically Events) are converted into the current format. This process typically occurs
 * with Domain Events that have been stored in an Event Store.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface UpcasterAware {

    /**
     * Sets the upcasters which allow older revisions of serialized objects to be deserialized. Upcasters are evaluated
     * in the order they are provided in the given List. That means that you should take special precaution when an
     * upcaster expects another upcaster to have processed an event.
     * <p/>
     * Any upcaster that relies on another upcaster doing its work first, should be placed <em>after</em> that other
     * upcaster in the given list. Thus for any <em>upcaster B</em> that relies on <em>upcaster A</em> to do its work
     * first, the following must be true: <code>upcasters.indexOf(B) > upcasters.indexOf(A)</code>.
     *
     * @param upcasters the upcasters for this serializer.
     */
    void setUpcasters(List<Upcaster> upcasters);
}
