package org.commonjava.aprox.infinispan.conf;

import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.commonjava.aprox.core.conf.AproxConfigInfo;
import org.commonjava.aprox.core.conf.AproxFeatureConfig;
import org.commonjava.web.config.ConfigurationException;
import org.commonjava.web.config.annotation.ConfigNames;
import org.commonjava.web.config.annotation.SectionName;

@SectionName( "infinispan" )
@Named( "use-factory-instead" )
public class CacheConfiguration
{
    @Singleton
    public static final class CacheFeatureConfig
        extends AproxFeatureConfig<CacheConfiguration, CacheConfiguration>
    {
        @Inject
        private CacheConfigInfo info;

        public CacheFeatureConfig()
        {
            super( CacheConfiguration.class );
        }

        @Produces
        @Default
        public CacheConfiguration getCacheConfig()
            throws ConfigurationException
        {
            return getConfig();
        }

        @Override
        public AproxConfigInfo getInfo()
        {
            return info;
        }
    }

    @Singleton
    public static final class CacheConfigInfo
        extends AproxConfigInfo
    {
        public CacheConfigInfo()
        {
            super( CacheConfiguration.class );
        }
    }

    public static final String DEFAULT_PATH = "/etc/aprox/infinispan.xml";

    private final String path;

    @ConfigNames( { "path" } )
    public CacheConfiguration( final String path )
    {
        this.path = path;
    }

    public final String getPath()
    {
        return path == null ? DEFAULT_PATH : path;
    }

}
