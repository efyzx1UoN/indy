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
package org.commonjava.indy.content.index;

import org.commonjava.indy.data.IndyDataException;
import org.commonjava.indy.data.StoreDataManager;
import org.commonjava.indy.model.core.ArtifactStore;
import org.commonjava.indy.model.core.StoreKey;
import org.commonjava.indy.model.core.StoreType;
import org.commonjava.indy.model.galley.GroupLocation;
import org.commonjava.indy.util.LocationUtils;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.spi.nfc.NotFoundCache;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * This listener is used to bind the content index cache event to nfc content clearing/adding. The following cases:
 * <ul>
 *     <li>When new content index entry added, it means that a new resource is available in repo(s),
 *     so all nfc content for this resource of the cascaded repos(low level concrete repo to higher group which contains it)
 *     should be cleared</li>
 *     <li>When content index entry removed, it means that the resource is missing in repo(s), so this resource with specified
 *     repo should be added into nfc. Note that only group level is processed here because remote and hosted are handled in
 *     other functions. </li>
 * </ul>
 *
 * So here we used ISPN event listener to handle these types of logic, by CacheEntryCreatedEvent and CacheEntryRemovedEvent
 */
@ApplicationScoped
@Listener
class NFCContentListener
{
    private final Logger logger = LoggerFactory.getLogger( this.getClass() );

    @Inject
    private StoreDataManager storeDataManager;

    @Inject
    private NotFoundCache nfc;

/*
 * Maven content index is by-directory which does not make sense for NFC because NFC is by concrete path.
 * For other package types, we'd better to move the clean-up to content manager itself in order to share some thing
 * important like the pre-loaded affected groups.
 *
    @CacheEntryCreated
    public void newIndex( final CacheEntryCreatedEvent<IndexedStorePath, IndexedStorePath> e )
    {
        if ( !e.isPre() )
        {
            IndexedStorePath isp = e.getValue();
            final StoreKey key =
                    new StoreKey( isp.getPackageType(), isp.getStoreType(), isp.getStoreName() );
            logger.debug( "New artifact created in store {} of path {}, will start to clear nfc cache for it.", key,
                          isp.getPath() );
            try
            {
                final ArtifactStore store = storeDataManager.getArtifactStore( key );
                nfc.clearMissing( new ConcreteResource( LocationUtils.toLocation( store ), isp.getPath() ) );
                nfcClearByContaining( store, isp.getPath() );
            }
            catch ( IndyDataException ex )
            {
                logger.error( String.format(
                        "When clear nfc missing for indexed artifact of path %s in store %s, failed to lookup store. Reason: %s",
                        isp.getPath(), key, ex.getMessage() ), ex );
            }
        }
    }
*/

    // Not sure if this entry modified event should be watched, need some further check
    //    @CacheEntryModified
    //    public void updateIndex( final CacheEntryModifiedEvent<IndexedStorePath, IndexedStorePath> e){
    //
    //    }

/*
 * Not need to aggressively add missing. In case of path P promoted to pnc-builds, it triggers removal of P in
 * affected groups, e.g, DA. This does not mean the DA/P is missing. We just let it rebuild next time when retrieved.
 *
    @CacheEntryRemoved
    public void removeIndex( final CacheEntryRemovedEvent<IndexedStorePath, IndexedStorePath> e )
    {
        if ( e.isPre() )
        {
            IndexedStorePath isp = e.getValue();
            final StoreKey key =
                    new StoreKey( isp.getPackageType(), isp.getStoreType(), isp.getStoreName() );
            // Only care about group level, as remote repo nfc is handled by remote timeout handler(see galley DownloadHandler),
            // and hosted is handled by DownloadManager
            if ( key.getType() == StoreType.group )
            {
                Location location = new GroupLocation( isp.getPackageType(), isp.getStoreName() );
                final ConcreteResource r = new ConcreteResource( location, isp.getPath() );
                nfc.addMissing( r );
            }
        }
    }

    private void nfcClearByContaining( final ArtifactStore store, final String path )
    {
        if ( store == null )
        {
            return;
        }
        final String name = String.format("ContentIndexNFCClean-store(%s)-path(%s)", store.getKey(), path  );
        final String context =
                String.format( "Class: %s, method: %s, store: %s, path: %s", this.getClass().getName(), "nfcClearByContaining",
                               store.getKey(), path );
        storeDataManager.asyncGroupAffectedBy( new StoreDataManager.ContextualTask(name, context, () -> {
            logger.debug( "Start to clear nfc for groups affected by {} of path {}", store, path );
            try
            {
                storeDataManager.query()
                                .packageType( store.getKey().getPackageType() )
                                .getGroupsAffectedBy( store.getKey() )
                                .forEach( g -> {
                                    final ConcreteResource r =
                                            new ConcreteResource( LocationUtils.toLocation( g ), path );
                                    logger.debug( "Clear NFC in terms of containing {} in {} for resource {}", store, g,
                                                  r );
                                    nfc.clearMissing( r );
                                } );
            }
            catch ( IndyDataException e )
            {
                logger.error(
                        String.format( "Failed to lookup parent stores which contain %s. Reason: %s", store.getKey(),
                                       e.getMessage() ), e );
            }
        } ) );
    }
*/
}
