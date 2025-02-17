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
package org.commonjava.indy.folo.data;

import org.commonjava.indy.IndyWorkflowException;
import org.commonjava.indy.conf.InternalFeatureConfig;
import org.commonjava.indy.folo.change.FoloBackupListener;
import org.commonjava.indy.folo.change.FoloExpirationWarningListener;
import org.commonjava.indy.folo.model.StoreEffect;
import org.commonjava.indy.folo.model.TrackedContent;
import org.commonjava.indy.folo.model.TrackedContentEntry;
import org.commonjava.indy.folo.model.TrackingKey;
import org.commonjava.o11yphant.metrics.annotation.Measure;
import org.commonjava.indy.subsys.infinispan.CacheHandle;
import org.infinispan.Cache;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.query.Search;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryBuilder;
import org.infinispan.query.dsl.QueryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@ApplicationScoped
@FoloStoretoInfinispan
public class FoloRecordCache implements FoloRecord {

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final int GET_ENTRIES_PAGE_SIZE = 300;

    @Inject
    private InternalFeatureConfig internalFeatureConfig;

    @FoloInprogressCache
    @Inject
    private CacheHandle<TrackedContentEntry, TrackedContentEntry> inProgressRecordCache;

    @FoloSealedCache
    @Inject
    private CacheHandle<TrackingKey, TrackedContent> sealedRecordCache;

    protected FoloRecordCache()
    {
    }

    @Inject
    private FoloBackupListener foloBackupListener;

    @Inject
    private FoloExpirationWarningListener expirationWarningListener;

    @PostConstruct
    private void init()
    {
        sealedRecordCache.executeCache( (cache) -> {
            cache.addListener( foloBackupListener );
            return null;
        } );

        inProgressRecordCache.executeCache( (cache) ->{
            cache.addListener( expirationWarningListener );
            return null;
        } );
    }

    public FoloRecordCache( final Cache<TrackedContentEntry, TrackedContentEntry> inProgressRecordCache,
                            final Cache<TrackingKey, TrackedContent> sealedRecordCache )
    {
        this.inProgressRecordCache = new CacheHandle("folo-in-progress", inProgressRecordCache);
        this.sealedRecordCache = new CacheHandle( "folo-sealed", sealedRecordCache );
    }

    /**
     * Add a new artifact upload/download item to given affected store within a tracked-content record. If the tracked-content record doesn't exist,
     * or doesn't contain the specified affected store, values will be created on-demand.
     * @param entry The TrackedContentEntry which will be cached
     * @return True if a new record was stored, otherwise false
     */
    @Override
    @Measure
    public synchronized boolean recordArtifact(final TrackedContentEntry entry)
            throws FoloContentException,IndyWorkflowException
    {
        if ( sealedRecordCache.containsKey( entry.getTrackingKey() ) )
        {
            throw new FoloContentException( "Tracking record: {} is already sealed!", entry.getTrackingKey() );
        }

        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.debug( "Adding tracking entry: {}", entry );
        return inProgressRecordCache.executeCache( (cache)->{
            TrackedContentEntry existing = cache.get( entry );
            if ( existing != null )
            {
                existing.merge( entry );
                cache.put( existing, existing );
            }
            else
            {
                cache.put( entry, entry );
            }

            return true;
        } );
    }

    @Override
    @Measure
    public synchronized void delete(final TrackingKey key)
    {
        sealedRecordCache.remove( key );
        inProgressByTrackingKey( key, (qb, ch)->{
            qb.build().list().forEach( item -> ch.execute( cache -> cache.remove( item ) ) );
            return false;
        } );
    }

    @Override
    public synchronized void replaceTrackingRecord(final TrackedContent record)
    {
        sealedRecordCache.put( record.getKey(), record );
    }

    @Override
    public synchronized boolean hasRecord(final TrackingKey key)
    {
        return hasSealedRecord( key ) || hasInProgressRecord( key );
    }

//    @Override
    public synchronized boolean hasSealedRecord(final TrackingKey key)
    {
        return sealedRecordCache.containsKey( key );
    }

//    @Override
    @Measure
    public synchronized boolean hasInProgressRecord(final TrackingKey key)
    {
        return !sealedRecordCache.containsKey( key ) && inProgressByTrackingKey( key, (qb, cacheHandle)->qb.build().getResultSize() > 0);
    }

    @Override
    public synchronized TrackedContent get(final TrackingKey key)
    {
        return sealedRecordCache.get( key );
    }

    @Override
    @Measure
    public TrackedContent seal(final TrackingKey trackingKey)
    {
        TrackedContent record = sealedRecordCache.get( trackingKey );

        Logger logger = LoggerFactory.getLogger( getClass() );
        if ( record != null )
        {
            logger.warn( "Tracking record: {} already sealed! Returning sealed record.", trackingKey );
            return record;
        }

        logger.info( "Listing unsealed tracking record entries, trackingKey: {}", trackingKey );
        return inProgressByTrackingKey( trackingKey, (qb, cacheHandle)-> {
            List<TrackedContentEntry> results = getTrackedContentEntries( qb );
            TrackedContent created = null;
            if ( results != null )
            {
                logger.debug( "Adding {} entries to record: {}", results.size(), trackingKey );
                Set<TrackedContentEntry> uploads = new TreeSet<>();
                Set<TrackedContentEntry> downloads = new TreeSet<>();
                results.forEach( ( result ) -> {
                    if ( StoreEffect.DOWNLOAD == result.getEffect() )
                    {
                        downloads.add( result );
                    }
                    else if ( StoreEffect.UPLOAD == result.getEffect() )
                    {
                        uploads.add( result );
                    }
                    logger.debug( "Removing in-progress entry: {}", result );
                    inProgressRecordCache.remove( result );
                } );
                created = new TrackedContent( trackingKey, uploads, downloads );
            }

            if ( created != null )
            {
                logger.info( "Sealing record for: {}", trackingKey );
                sealedRecordCache.put( trackingKey, created );
            }
            return created;
        });
    }

    private List<TrackedContentEntry> getTrackedContentEntries( QueryBuilder qb )
    {
        List<TrackedContentEntry> results;
        if ( internalFeatureConfig != null && !internalFeatureConfig.getFoloISPNQueryPaginationEnabled() )
        {
            results = qb.build().list();
        }
        else // use pagination
        {
            results = new ArrayList<>();

            Query query = qb.build();
            int size = query.getResultSize();
            logger.info( "Query TrackedContentEntry, size: {}", size );
            if ( size <= 0 )
            {
                return results;
            }

            int total = 0;
            int offset = 0;
            while ( total < size )
            {
                query = qb.maxResults( GET_ENTRIES_PAGE_SIZE ).build();
                query.startOffset( offset );
                List<TrackedContentEntry> ret = query.list();
                if ( ret == null || ret.isEmpty() )
                {
                    logger.info( "Query TrackedContentEntry get null or empty, {}", ret );
                    break;
                }
                logger.debug( "Get TrackedContentEntry, size: {}, offset: {}", ret.size(), offset );
                total += ret.size();
                offset += ret.size();
                results.addAll( ret );
            }

            if ( results.size() != size )
            {
                logger.error( "Query TrackedContentEntry size error, size: {}, expected: {}", results.size(), size );
                return null;
            }
        }
        return results;
    }

    @Override
    public Set<TrackingKey> getInProgressTrackingKey()
    {
        return inProgressRecordCache.execute( BasicCache::keySet )
                                    .stream()
                                    .map( TrackedContentEntry::getTrackingKey )
                                    .collect( Collectors.toSet() );
    }

    @Override
    public Set<TrackingKey> getSealedTrackingKey()
    {
        return sealedRecordCache.execute( BasicCache::keySet );
    }

    @Override
    public Set<TrackedContent> getSealed()
    {
        return sealedRecordCache.execute( BasicCache::entrySet ).stream().map( (et) -> et.getValue() ).collect( Collectors.toSet() );
    }

    private <R> R inProgressByTrackingKey( final TrackingKey key, final BiFunction<QueryBuilder, CacheHandle<TrackedContentEntry, TrackedContentEntry>, R> operation )
    {
        return inProgressRecordCache.executeCache( ( cache ) -> {
            QueryFactory queryFactory = Search.getQueryFactory( cache );
            QueryBuilder qb = queryFactory.from( TrackedContentEntry.class )
                                             .having( "trackingKey.id" )
                                             .eq( key.getId() )
                                             .toBuilder();
            // FIXME: Ordering breaks the query parser (it expects a LPAREN for some reason, and adding it to the string below doesn't work)
//                                             .orderBy( "index", SortOrder.ASC );

            return operation.apply( qb, inProgressRecordCache );
        } );
    }

    @Override
    public void addSealedRecord(TrackedContent record)
    {
        sealedRecordCache.put( record.getKey(), record );
    }
}
