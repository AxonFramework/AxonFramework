package org.axonframework.messaging;

import java.util.Objects;

public record SimpleQualifiedName(String namespace, String localName) implements QualifiedName {

    public SimpleQualifiedName(String namespace, String localName) {
        this.namespace = Objects.requireNonNullElse(namespace, "");
        this.localName = Objects.requireNonNull(localName, "localName must not be null");
    }

    @Override
    public String toString() {
        return namespace.isEmpty() ? localName : namespace + "." + localName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QualifiedName that)) {
            return false;
        }
        return namespace.equals(that.namespace()) && localName.equals(that.localName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, localName);
    }

    @Override
    public String revision() {
        return null;
    }
}
