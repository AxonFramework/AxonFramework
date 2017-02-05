package org.axonframework.serializer.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by cdavis & graham brooks on 6/11/15.
 */
public abstract class AssociationValueMixin {
    AssociationValueMixin(@JsonProperty("key") String key, @JsonProperty("value") String value ) { }
}
