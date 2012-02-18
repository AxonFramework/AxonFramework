package org.axonframework.serializer.bson;

import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import org.axonframework.serializer.AbstractContentTypeConverter;

/**
 * ContentTypeConverter implementation that converts a DBObject structure into a String containing its Binary JSON
 * representation.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DBObjectToStringContentTypeConverter extends AbstractContentTypeConverter<DBObject, String> {

    @Override
    public Class<DBObject> expectedSourceType() {
        return DBObject.class;
    }

    @Override
    public Class<String> targetType() {
        return String.class;
    }

    @Override
    public String convert(DBObject original) {
        return JSON.serialize(original);
    }
}
