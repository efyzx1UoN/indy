package org.commonjava.indy.subsys.honeycomb;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.SpanBuilderFactory;
import io.honeycomb.beeline.tracing.SpanPostProcessor;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.Tracing;
import io.honeycomb.beeline.tracing.sampling.Sampling;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.LibHoney;
import org.commonjava.indy.subsys.honeycomb.config.HoneycombConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@ApplicationScoped
public class HoneycombManager
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private HoneyClient client;

    private Beeline beeline;

    @Inject
    private HoneycombConfiguration configuration;

    public HoneycombManager()
    {
    }

    @PostConstruct
    public void init()
    {
        String writeKey = configuration.getWriteKey();
        if ( isNotBlank( writeKey ) )
        {
            logger.info( "Start Honeycomb manager" );
            client = LibHoney.create( LibHoney.options()
                                              .setDataset( configuration.getDataset() )
                                              .setWriteKey( writeKey )
                                              .build() );
            SpanPostProcessor postProcessor = Tracing.createSpanProcessor( client, Sampling.alwaysSampler() );
            SpanBuilderFactory factory = Tracing.createSpanBuilderFactory( postProcessor, Sampling.alwaysSampler() );
            Tracer tracer = Tracing.createTracer( factory );
            beeline = Tracing.createBeeline( tracer, factory );
        }
    }

    public HoneyClient getClient()
    {
        return client;
    }

    public Beeline getBeeline()
    {
        return beeline;
    }

    public Span startRootTracer( String spanName )
    {
        if ( beeline != null )
        {
            Span rootSpan = beeline.getSpanBuilderFactory()
                                   .createBuilder()
                                   .setSpanName( spanName )
                                   .setServiceName( "indy" )
                                   .build();
            beeline.getTracer().startTrace( rootSpan );
            return rootSpan;
        }
        return null;
    }

    public Span startChildSpan( String spanName )
    {
        if ( beeline != null )
        {
            beeline.startChildSpan( spanName );
        }
        return null;
    }
}
