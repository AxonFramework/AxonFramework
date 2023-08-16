package org.axonframework.messaging;

public interface QualifiedName {

    static QualifiedName fromClass(Class<?> clazz) {
        return new SimpleQualifiedName(clazz.getPackageName(), clazz.getSimpleName());
    }

    static QualifiedName fromDottedName(String dottedName) {
        int lastDot = dottedName.lastIndexOf('.');
        return new SimpleQualifiedName(dottedName.substring(0, Math.max(lastDot, 0)),
                                 dottedName.substring(lastDot + 1));
    }

    default String toSimpleString() {
        return localName() + (namespace().isBlank() ? "" : " (" + namespace() + ")");
    }

    String namespace();

    String localName();

}
