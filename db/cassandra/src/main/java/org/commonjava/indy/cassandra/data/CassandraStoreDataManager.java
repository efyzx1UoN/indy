package org.commonjava.indy.cassandra.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.commonjava.indy.audit.ChangeSummary;
import org.commonjava.indy.data.IndyDataException;
import org.commonjava.indy.data.StoreEventDispatcher;
import org.commonjava.indy.db.common.AbstractStoreDataManager;
import org.commonjava.indy.model.core.ArtifactStore;
import org.commonjava.indy.model.core.Group;
import org.commonjava.indy.model.core.HostedRepository;
import org.commonjava.indy.model.core.RemoteRepository;
import org.commonjava.indy.model.core.StoreKey;
import org.commonjava.indy.model.core.StoreType;
import org.commonjava.indy.model.core.io.IndyObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
@Alternative
public class CassandraStoreDataManager extends AbstractStoreDataManager
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    CassandraStoreQuery storeQuery;

    @Inject
    IndyObjectMapper objectMapper;

    @Override
    protected StoreEventDispatcher getStoreEventDispatcher()
    {
        return null;
    }

    @Override
    protected ArtifactStore getArtifactStoreInternal( StoreKey key )
    {
        DtxArtifactStore dtxArtifactStore = storeQuery.getArtifactStore( key.getPackageType(), key.getType(), key.getName() );

        return toArtifactStore( dtxArtifactStore );
    }

    @Override
    protected ArtifactStore removeArtifactStoreInternal( StoreKey key )
    {
        DtxArtifactStore dtxArtifactStore = storeQuery.removeArtifactStore( key.getPackageType(), key.getType(), key.getName() );
        return toArtifactStore( dtxArtifactStore );
    }

    @Override
    public void clear( ChangeSummary summary ) throws IndyDataException
    {

    }

    @Override
    public Set<ArtifactStore> getAllArtifactStores()
    {
        Set<DtxArtifactStore> dtxArtifactStoreSet = storeQuery.getAllArtifactStores();
        Set<ArtifactStore> artifactStoreSet = new HashSet<>(  );
        dtxArtifactStoreSet.forEach( dtxArtifactStore -> {
            artifactStoreSet.add( toArtifactStore( dtxArtifactStore ) );
        } );
        return artifactStoreSet;
    }

    @Override
    public Map<StoreKey, ArtifactStore> getArtifactStoresByKey()
    {
        Map<StoreKey, ArtifactStore> ret = new HashMap<>();
        Set<ArtifactStore> artifactStoreSet = getAllArtifactStores();
        artifactStoreSet.forEach( store -> {
            ret.put( store.getKey(), store );
        } );
        return ret;
    }

    @Override
    public boolean hasArtifactStore( StoreKey key )
    {
        ArtifactStore artifactStore = getArtifactStoreInternal( key );
        return artifactStore != null;
    }

    @Override
    public boolean isStarted()
    {
        return false;
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public Stream<StoreKey> streamArtifactStoreKeys()
    {
        return null;
    }

    @Override
    protected ArtifactStore putArtifactStoreInternal( StoreKey storeKey, ArtifactStore store )
    {
        DtxArtifactStore dtxArtifactStore = toDtxArtifactStore( storeKey, store );
        storeQuery.createDtxArtifactStore( dtxArtifactStore );

        return toArtifactStore( storeQuery.getArtifactStore( storeKey.getPackageType(), storeKey.getType(), storeKey.getName() ));
    }

    private DtxArtifactStore toDtxArtifactStore( StoreKey storeKey, ArtifactStore store )
    {

        DtxArtifactStore dtxArtifactStore = new DtxArtifactStore();
        dtxArtifactStore.setPackageType( storeKey.getPackageType() );
        dtxArtifactStore.setStoreType( storeKey.getType().name() );
        dtxArtifactStore.setName( storeKey.getName() );
        dtxArtifactStore.setDisableTimeout( store.getDisableTimeout() );
        dtxArtifactStore.setMetadata( store.getMetadata() );
        dtxArtifactStore.setDescription( store.getDescription() );
        dtxArtifactStore.setCreateTime( store.getCreateTime() );
        dtxArtifactStore.setAuthoritativeIndex( store.isAuthoritativeIndex() );
        dtxArtifactStore.setPathStyle( store.getPathStyle().name() );
        dtxArtifactStore.setRescanInProgress( store.isRescanInProgress() );
        dtxArtifactStore.setPathMaskPatterns( store.getPathMaskPatterns() );
        dtxArtifactStore.setDisabled( store.isDisabled() );
        dtxArtifactStore.setExtras( toExtra( store) );

        return dtxArtifactStore;
    }

    private Map<String, String> toExtra( ArtifactStore store )
    {
        Map<String, String> extras = new HashMap<>(  );
        if ( store instanceof HostedRepository )
        {
            HostedRepository hostedRepository = (HostedRepository) store;
            putValueIntoExtra( CassandraStoreUtil.STORAGE, hostedRepository.getStorage(), extras );
            putValueIntoExtra( CassandraStoreUtil.READONLY, hostedRepository.isReadonly(), extras );
            putValueIntoExtra( CassandraStoreUtil.SNAPSHOT_TIMEOUT_SECONDS, hostedRepository.getSnapshotTimeoutSeconds(), extras );
        }
        if ( store instanceof RemoteRepository )
        {
            RemoteRepository remoteRepository = (RemoteRepository) store;
            putValueIntoExtra( CassandraStoreUtil.URL, remoteRepository.getUrl(), extras);
            putValueIntoExtra( CassandraStoreUtil.HOST, remoteRepository.getHost(), extras );
            putValueIntoExtra( CassandraStoreUtil.PORT, remoteRepository.getPort(), extras );
            putValueIntoExtra( CassandraStoreUtil.USER, remoteRepository.getUser(), extras );
            putValueIntoExtra( CassandraStoreUtil.PASSWORD, remoteRepository.getPassword(), extras );
            putValueIntoExtra( CassandraStoreUtil.PROXY_HOST, remoteRepository.getProxyHost(), extras );
            putValueIntoExtra( CassandraStoreUtil.PROXY_PORT, remoteRepository.getProxyPort(), extras );
            putValueIntoExtra( CassandraStoreUtil.PROXY_USER, remoteRepository.getProxyUser(), extras );
            putValueIntoExtra( CassandraStoreUtil.PROXY_PASSWORD, remoteRepository.getProxyPassword(), extras );
            putValueIntoExtra( CassandraStoreUtil.KEY_CERT_PEM, remoteRepository.getKeyCertPem(), extras );
            putValueIntoExtra( CassandraStoreUtil.KEY_PASSWORD, remoteRepository.getKeyPassword(), extras );
            putValueIntoExtra( CassandraStoreUtil.SERVER_CERT_PEM, remoteRepository.getServerCertPem(), extras );
            putValueIntoExtra( CassandraStoreUtil.PREFETCH_RESCAN_TIMESTAMP, remoteRepository.getPrefetchRescanTimestamp(), extras );
            putValueIntoExtra( CassandraStoreUtil.METADATA_TIMEOUT_SECONDS, remoteRepository.getMetadataTimeoutSeconds(), extras );
            putValueIntoExtra( CassandraStoreUtil.CACHE_TIMEOUT_SECONDS, remoteRepository.getCacheTimeoutSeconds(), extras );
            putValueIntoExtra( CassandraStoreUtil.TIMEOUT_SECONDS, remoteRepository.getTimeoutSeconds(), extras );
            putValueIntoExtra( CassandraStoreUtil.MAX_CONNECTIONS, remoteRepository.getMaxConnections(), extras );
            putValueIntoExtra( CassandraStoreUtil.NFC_TIMEOUT_SECONDS, remoteRepository.getNfcTimeoutSeconds(), extras );
            putValueIntoExtra( CassandraStoreUtil.PASS_THROUGH, remoteRepository.isPassthrough(), extras );
            putValueIntoExtra( CassandraStoreUtil.PREFETCH_RESCAN, remoteRepository.isPrefetchRescan(), extras );
            putValueIntoExtra( CassandraStoreUtil.IGNORE_HOST_NAME_VERIFICATION, remoteRepository.isIgnoreHostnameVerification(), extras );

        }
        if ( store instanceof Group )
        {
            Group group = ( Group ) store;
            putValueIntoExtra( CassandraStoreUtil.CONSTITUENTS, group.getConstituents(), extras );
            putValueIntoExtra( CassandraStoreUtil.PREPEND_CONSTITUENT, group.isPrependConstituent(), extras );
        }
        return extras;
    }

    private void putValueIntoExtra( String key, Object value, Map<String, String> extras )
    {
        if ( value != null )
        {
            try
            {
                extras.put( key, objectMapper.writeValueAsString( value ) );
            }
            catch ( JsonProcessingException e )
            {
                logger.warn( "Write value into extra error, key: {}", key, e );
            }
        }
    }

    private ArtifactStore toArtifactStore( final DtxArtifactStore dtxArtifactStore )
    {
        if ( dtxArtifactStore == null )
        {
            return null;
        }
        ArtifactStore store = generateStore( dtxArtifactStore );

        if ( store != null )
        {
            store.setDisabled( dtxArtifactStore.isDisabled() );
            store.setMetadata( dtxArtifactStore.getMetadata() );
            store.setRescanInProgress( dtxArtifactStore.getRescanInProgress() );
            store.setDescription( dtxArtifactStore.getDescription() );
            store.setPathMaskPatterns( dtxArtifactStore.getPathMaskPatterns() );
            store.setDisableTimeout( dtxArtifactStore.getDisableTimeout() );
            store.setAuthoritativeIndex( dtxArtifactStore.getAuthoritativeIndex() );
        }
        return store;
    }

    private ArtifactStore generateStore( DtxArtifactStore dtxArtifactStore )
    {
        ArtifactStore store = null;
        if ( dtxArtifactStore.getExtras() != null && !dtxArtifactStore.getExtras().isEmpty() )
        {
            if ( dtxArtifactStore.getStoreType().equals( StoreType.hosted.name() ) )
            {
                store = new HostedRepository( dtxArtifactStore.getPackageType(), dtxArtifactStore.getName() );
                ( (HostedRepository) store ).setReadonly( readValueFromExtra( CassandraStoreUtil.READONLY, Boolean.class, dtxArtifactStore.getExtras() ));
                Integer snapshotTimeoutseconds = readValueFromExtra( CassandraStoreUtil.SNAPSHOT_TIMEOUT_SECONDS, Integer.class, dtxArtifactStore.getExtras());
                if ( snapshotTimeoutseconds != null )
                {
                    ( (HostedRepository) store ).setSnapshotTimeoutSeconds( snapshotTimeoutseconds.intValue() );
                }
                ( (HostedRepository) store ).setStorage( dtxArtifactStore.getExtras().get( CassandraStoreUtil.STORAGE ) );
            }
            else if ( dtxArtifactStore.getStoreType().equals( StoreType.remote.name() ) )
            {
                store = new RemoteRepository( dtxArtifactStore.getPackageType(), dtxArtifactStore.getName(),
                                              dtxArtifactStore.getExtras().get( CassandraStoreUtil.URL ) );
                setIfNotNull( ( (RemoteRepository) store )::setUser, dtxArtifactStore.getExtras().get( CassandraStoreUtil.USER ) );
                setIfNotNull( ( (RemoteRepository) store )::setPassword, dtxArtifactStore.getExtras().get( CassandraStoreUtil.PASSWORD ) );
                setIfNotNull( ( (RemoteRepository) store )::setHost, dtxArtifactStore.getExtras().get( CassandraStoreUtil.HOST ) );
                setIfNotNull( ( (RemoteRepository) store )::setProxyHost, dtxArtifactStore.getExtras().get( CassandraStoreUtil.PROXY_HOST ) );
                setIfNotNull( ( (RemoteRepository) store )::setServerCertPem, dtxArtifactStore.getExtras().get( CassandraStoreUtil.SERVER_CERT_PEM ) );
                setIfNotNull( ( (RemoteRepository) store )::setKeyCertPem, dtxArtifactStore.getExtras().get( CassandraStoreUtil.KEY_CERT_PEM ) );
                setIfNotNull( ( (RemoteRepository) store )::setKeyPassword, dtxArtifactStore.getExtras().get( CassandraStoreUtil.KEY_PASSWORD ) );
                setIfNotNull( ( (RemoteRepository) store )::setProxyPassword, dtxArtifactStore.getExtras().get( CassandraStoreUtil.PROXY_PASSWORD ) );
                setIfNotNull( ( (RemoteRepository) store )::setProxyUser, dtxArtifactStore.getExtras().get( CassandraStoreUtil.PROXY_USER ) );
                setIfNotNull( ( (RemoteRepository) store )::setPrefetchRescanTimestamp, dtxArtifactStore.getExtras().get( CassandraStoreUtil.PREFETCH_RESCAN_TIMESTAMP ) );

                Integer timeoutSeconds = readValueFromExtra( CassandraStoreUtil.TIMEOUT_SECONDS, Integer.class, dtxArtifactStore.getExtras());
                if ( timeoutSeconds != null )
                {
                    ( (RemoteRepository) store ).setTimeoutSeconds( timeoutSeconds.intValue() );
                }
                Integer metadataTimeoutSeconds = readValueFromExtra( CassandraStoreUtil.METADATA_TIMEOUT_SECONDS, Integer.class, dtxArtifactStore.getExtras());
                if ( metadataTimeoutSeconds != null )
                {
                    ( (RemoteRepository) store ).setMetadataTimeoutSeconds( metadataTimeoutSeconds.intValue() );
                }
                Integer cacheTimeoutSeconds = readValueFromExtra( CassandraStoreUtil.CACHE_TIMEOUT_SECONDS, Integer.class, dtxArtifactStore.getExtras());
                if ( cacheTimeoutSeconds != null )
                {
                    ( (RemoteRepository) store ).setCacheTimeoutSeconds( cacheTimeoutSeconds.intValue() );
                }
                Integer nfcTimeoutSeconds = readValueFromExtra( CassandraStoreUtil.NFC_TIMEOUT_SECONDS, Integer.class, dtxArtifactStore.getExtras());
                if ( nfcTimeoutSeconds != null )
                {
                    ( (RemoteRepository) store ).setNfcTimeoutSeconds( nfcTimeoutSeconds.intValue() );
                }
                Integer maxConnections = readValueFromExtra( CassandraStoreUtil.MAX_CONNECTIONS, Integer.class, dtxArtifactStore.getExtras());
                if ( maxConnections != null )
                {
                    ( (RemoteRepository) store ).setMaxConnections( maxConnections.intValue() );
                }
                Integer port = readValueFromExtra( CassandraStoreUtil.PORT, Integer.class, dtxArtifactStore.getExtras());
                if ( port != null )
                {
                    ( (RemoteRepository) store ).setPort( port.intValue() );
                }
                Integer proxyPort = readValueFromExtra( CassandraStoreUtil.PROXY_PORT, Integer.class, dtxArtifactStore.getExtras());
                if ( proxyPort != null )
                {
                    ( (RemoteRepository) store ).setProxyPort( proxyPort.intValue() );
                }
                Boolean prefetchRescan = readValueFromExtra( CassandraStoreUtil.PREFETCH_RESCAN, Boolean.class, dtxArtifactStore.getExtras());
                if ( prefetchRescan != null )
                {
                    ( (RemoteRepository) store ).setPrefetchRescan( prefetchRescan );
                }
                Boolean passThrough = readValueFromExtra( CassandraStoreUtil.PASS_THROUGH, Boolean.class, dtxArtifactStore.getExtras());
                if ( passThrough != null )
                {
                    ( (RemoteRepository) store ).setPassthrough( passThrough );
                }
                Boolean ignoreHostnameVerification = readValueFromExtra( CassandraStoreUtil.IGNORE_HOST_NAME_VERIFICATION, Boolean.class, dtxArtifactStore.getExtras());
                if ( ignoreHostnameVerification != null )
                {
                    ( (RemoteRepository) store ).setIgnoreHostnameVerification( ignoreHostnameVerification );
                }
            }
            else if ( dtxArtifactStore.getStoreType().equals( StoreType.group.name() ) )
            {
                List<String> constituentStrList = readValueFromExtra( CassandraStoreUtil.CONSTITUENTS, List.class, dtxArtifactStore.getExtras() );
                List<StoreKey> constituentList = constituentStrList.stream().map( item -> StoreKey.fromString( item ) ).collect( Collectors.toList() );
                store = new Group( dtxArtifactStore.getPackageType(), dtxArtifactStore.getName(), constituentList );

                Boolean prependConstituent = readValueFromExtra( CassandraStoreUtil.PREPEND_CONSTITUENT, Boolean.class, dtxArtifactStore.getExtras());
                if ( prependConstituent != null )
                {
                    ( (Group) store ).setPrependConstituent( prependConstituent );
                }
            }
        }
        return store;
    }

    private <T> void setIfNotNull( final Consumer<T> setter, final T value) {
        if (value != null) {
            setter.accept(value);
        }
    }

    public <T> T readValueFromExtra(String key, Class<T> valueType, Map<String, String> extras )
    {
        try
        {
            if ( extras.get( key ) != null )
            {
                return objectMapper.readValue( extras.get( key ), valueType );
            }
        }
        catch ( JsonProcessingException e )
        {
            logger.warn( "Read value from extra error, key: {}.", key, e );
        }
        return null;
    }

}
