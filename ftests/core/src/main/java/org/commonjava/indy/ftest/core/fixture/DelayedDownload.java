/**
 * Copyright (C) 2011-2022 Red Hat, Inc. (https://github.com/Commonjava/indy)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.indy.ftest.core.fixture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.commonjava.indy.client.core.Indy;
import org.commonjava.indy.client.core.IndyClientException;
import org.commonjava.indy.model.core.StoreKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DelayedDownload
    implements Runnable
{
    private final long initialDelay;
    private final Indy client;
    
    private final StoreKey key;
    private final String path;
    
    private long startTime;
    private long endTime;
    private ByteArrayOutputStream content;

    private final CountDownLatch latch;

    private boolean missing;
    
    public DelayedDownload( final Indy client, final StoreKey key, final String path, final long initialDelay,
                            final CountDownLatch latch )
    {
        this.client = client;
        this.key = key;
        this.path = path;
        this.initialDelay = initialDelay;
        this.latch = latch;
    }
    
    @Override
    public void run()
    {
        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.info( "Starting: {}", Thread.currentThread().getName() );

        if ( initialDelay > 0 )
        {
            logger.info( "Delaying: {}", initialDelay );

            try
            {
                Thread.sleep( initialDelay );
            }
            catch ( final InterruptedException e )
            {
                return;
            }
        }
        
        startTime = System.nanoTime();
        content = new ByteArrayOutputStream();

        logger.info( "Trying: {}", Thread.currentThread().getName() );
        try(InputStream in = client.content().get( key, path ))
        {
            if ( in == null )
            {
                missing = true;
            }
            else
            {
                CountingInputStream cin = new CountingInputStream( in );
                IOUtils.copy( cin, content );
                logger.debug( "Read: {} bytes", cin.getByteCount() );
            }
        }
        catch ( IndyClientException | IOException e )
        {
            logger.error( e.getMessage(), e );
        }

        endTime = System.nanoTime();
        latch.countDown();

        logger.info( "Stopping: {}", Thread.currentThread().getName() );
    }
    
    public boolean isMissing()
    {
        return missing;
    }

    public long getStartTime()
    {
        return startTime;
    }
    
    public long getEndTime()
    {
        return endTime;
    }
    
    public ByteArrayOutputStream getContent()
    {
        return content;
    }
}