package org.axonframework.eventstore.mongo.criteria;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * Implementation of Collection operators for the Mongo Criteria, such as "In" and "NotIn".
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class CollectionCriteria extends MongoCriteria {

    private final MongoProperty property;
    private final Object expression;
    private final String operator;

    /**
     * Returns a criterion that requires the given <code>property</code> value to be present in the given
     * <code>expression</code> to evaluate to <code>true</code>.
     *
     * @param property   The property to match
     * @param operator The collection operator to use
     * @param expression The expression to that expresses the collection to match against the property
     */
    public CollectionCriteria(MongoProperty property, String operator, Object expression) {
        this.property = property;
        this.expression = expression;
        this.operator = operator;
    }

    @Override
    public DBObject asMongoObject() {
        return new BasicDBObject(property.getName(), new BasicDBObject(operator, expression));
    }
}
