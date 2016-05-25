package org.axonframework.eventstore.fs;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;

import org.axonframework.eventstore.fs.SimpleEventFileResolver;

/**
 * Variant of SimpleEventFileResolver that allows to iterate through all aggregates (to replay events)
 * @author mwyraz
 */
public class SimpleListableEventFileResolver extends SimpleEventFileResolver implements ListableEventFileResolver
{
    public SimpleListableEventFileResolver(File baseDir)
    {
        super(baseDir);
    }
    
    @Override
    public void listEvents(EventFileListConsumer consumer)
    {
        final String EXT="."+FILE_EXTENSION_EVENTS;
        File[] typeDirs=baseDir.listFiles();
        if (typeDirs!=null) for (File typeDir: typeDirs)
        {
            if (!typeDir.isDirectory()) continue;
            File[] eventFiles=typeDir.listFiles();
            if (eventFiles!=null) for (File eventFile: eventFiles)
            {
                if (!eventFile.isFile()) continue;
                if (!eventFile.getName().endsWith(EXT)) continue;
                
                String decodedEventId;
                try
                {
                    decodedEventId=URLDecoder.decode(eventFile.getName().substring(0,eventFile.getName().length()-EXT.length()),"UTF-8");
                }
                catch (IOException ex)
                {
                    throw new IllegalStateException("UTF-8 not supported by vm?");
                }
                consumer.consume(typeDir.getName(), decodedEventId);
            }
        }
    }
    
}
