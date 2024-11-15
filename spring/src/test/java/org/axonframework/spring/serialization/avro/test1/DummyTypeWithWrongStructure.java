package org.axonframework.spring.serialization.avro.test1;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;

public class DummyTypeWithWrongStructure extends SpecificRecordBase {
    @Override
    public void put(int i, Object v) {

    }

    @Override
    public Object get(int i) {
        return null;
    }

    @Override
    public Schema getSchema() {
        return null;
    }
}
