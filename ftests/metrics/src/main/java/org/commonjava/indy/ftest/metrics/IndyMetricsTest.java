package org.commonjava.indy.ftest.metrics;

import org.commonjava.indy.client.core.IndyClientModule;
import org.commonjava.indy.ftest.core.AbstractIndyFunctionalTest;
import org.commonjava.indy.ftest.metrics.client.IndyMetricsFtestClientModule;
import org.commonjava.indy.test.fixture.core.CoreServerFixture;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

/**
 * Created by xiabai on 3/22/17.
 */
public class IndyMetricsTest
                extends AbstractIndyFunctionalTest
{
    @Test
    public void MetricsTest() throws Exception
    {
        for ( int i = 0; i < 10; i++ )
        {
            client.module( IndyMetricsFtestClientModule.class ).getTimerWithOutException();
        }

        for ( int i = 0; i < 10; i++ )
        {
            try
            {
                client.module( IndyMetricsFtestClientModule.class ).getTimerWithException();
            }
            catch ( Throwable throwable )
            {
                //do nothing
            }
        }

        for ( int i = 0; i < 10; i++ )
        {
            client.module( IndyMetricsFtestClientModule.class ).getMeterWithOutException();
        }

        for ( int i = 0; i < 10; i++ )
        {
            try
            {
                client.module( IndyMetricsFtestClientModule.class ).getMeterWithException();
            }
            catch ( Throwable throwable )
            {
                //do nothing
            }
        }
        Thread.sleep( 5000 );

        assertEquals( "20", client.module( IndyMetricsFtestClientModule.class ).getMeterCount() );
        assertEquals( "20", client.module( IndyMetricsFtestClientModule.class ).getTimerCount() );
        assertEquals( "10", client.module( IndyMetricsFtestClientModule.class ).getMeterCountWithException());
        assertEquals( "10", client.module( IndyMetricsFtestClientModule.class ).getTimerCountWithException() );
    }

    @Override
    protected Collection<IndyClientModule> getAdditionalClientModules()
    {
        return Arrays.<IndyClientModule>asList( new IndyMetricsFtestClientModule() );
    }

    @Override
    protected void initTestConfig( CoreServerFixture fixture ) throws IOException
    {
        writeConfigFile( "conf.d/metrics.conf", "\n"+readTestResource( "default-test-metrics.conf" ) );
    }
}
