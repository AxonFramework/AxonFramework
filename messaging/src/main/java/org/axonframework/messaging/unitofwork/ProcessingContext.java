package org.axonframework.messaging.unitofwork;

/**
 * @author Awesome People
 */
// TODO why does ProcessingContext extends ProcessingLifecycle ?
public interface ProcessingContext extends ProcessingLifecycle {

    Resources resources(ResourceScope scope);

    default Resources localResources() {
        return resources(ResourceScope.LOCAL);
    }

    default Resources inheritedResources() {
        return resources(ResourceScope.INHERITED);
    }

    default Resources sharedResources() {
        return resources(ResourceScope.SHARED);
    }

    boolean isStarted();

    boolean isRolledBack();

    boolean isCommitted();

    boolean isCompleted();

    // TODO do we need different resource scopes? smells likes unit of work nesting to me!
    enum ResourceScope {
        LOCAL,
        INHERITED,
        SHARED
    }
}
