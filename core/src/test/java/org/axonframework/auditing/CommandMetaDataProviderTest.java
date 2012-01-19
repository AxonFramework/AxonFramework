package org.axonframework.auditing;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.domain.MetaData;
import org.junit.*;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class CommandMetaDataProviderTest {

    @Test
    public void testProvideAuditData() {
        MetaData metaData = MetaData.emptyInstance();
        GenericCommandMessage<String> message = new GenericCommandMessage<String>("command", metaData);

        Map<String, Object> actual = new CommandMetaDataProvider().provideAuditDataFor(message);
        assertSame(metaData, actual);
    }
}
