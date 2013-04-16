package org.axonframework.eventstore.management;

/**
 * Abstract class implementing the generic limit part of the {@link Criteria} interface.
 *
 * @author Jason Fagan
 * @since 2.1
 */
public abstract class AbstractCriteria implements Criteria {

    private int startAt = -1;
    private int batchSize = -1;

    /**
     * {@inheritDoc}
     */
    @Override
    public Criteria limit(int start, int batchSize) {
        if (hasLimit()) {
            throw new IllegalStateException("limit must only be called once on a criteria");
        }
        this.startAt = start;
        this.batchSize = batchSize;

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getStartAt() {
        return startAt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasLimit() {
        return startAt != -1 && batchSize != -1;
    }
}
