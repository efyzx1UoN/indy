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
package org.commonjava.indy.pkg.maven.content;

import org.commonjava.indy.IndyWorkflowException;
import org.commonjava.indy.content.DirectContentAccess;
import org.commonjava.indy.content.StoreResource;
import org.commonjava.indy.core.content.AbstractMergedContentGenerator;
import org.commonjava.indy.content.MergedContentAction;
import org.commonjava.indy.core.content.group.GroupMergeHelper;
import org.commonjava.indy.data.StoreDataManager;
import org.commonjava.indy.model.core.ArtifactStore;
import org.commonjava.indy.model.core.Group;
import org.commonjava.indy.pkg.maven.content.group.ArchetypeCatalogMerger;
import org.commonjava.indy.util.LocationUtils;
import org.commonjava.maven.galley.event.EventMetadata;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.model.TransferOperation;
import org.commonjava.maven.galley.spi.nfc.NotFoundCache;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ArchetypeCatalogGenerator
        extends AbstractMergedContentGenerator
{

    private static final Set<String> HANDLED_FILENAMES = Collections.unmodifiableSet( new HashSet<String>()
    {

        {
            add( ArchetypeCatalogMerger.CATALOG_NAME );
            add( ArchetypeCatalogMerger.CATALOG_MD5_NAME );
            add( ArchetypeCatalogMerger.CATALOG_SHA_NAME );
            add( ArchetypeCatalogMerger.CATALOG_SHA256_NAME );
            add( ArchetypeCatalogMerger.CATALOG_SHA384_NAME );
            add( ArchetypeCatalogMerger.CATALOG_SHA512_NAME );
        }

        private static final long serialVersionUID = 1L;

    } );

    @Inject
    private ArchetypeCatalogMerger merger;

    protected ArchetypeCatalogGenerator()
    {
    }

    public ArchetypeCatalogGenerator( final DirectContentAccess downloadManager, final StoreDataManager storeManager,
                                      final ArchetypeCatalogMerger merger, final GroupMergeHelper mergeHelper,
                                      final NotFoundCache nfc, final MergedContentAction... mergedContentActions )
    {
        super( downloadManager, storeManager, mergeHelper, nfc, mergedContentActions );
        this.merger = merger;
        this.helper = mergeHelper;
    }

    @Override
    public boolean canProcess( final String path )
    {
        for ( final String filename : HANDLED_FILENAMES )
        {
            if ( path.endsWith( filename ) )
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public Transfer generateGroupFileContent( final Group group, final List<ArtifactStore> members, final String path,
                                              final EventMetadata eventMetadata )
        throws IndyWorkflowException
    {
        if ( !canProcess( path ) )
        {
            return null;
        }

        final Transfer target = fileManager.getTransfer( group, path );

        if ( !target.exists() )
        {
            // For .md5, sha1, sha256, etc, we need to get the raw archetype path to merge to.
            String toMergePath = path;
            if ( !path.endsWith( ArchetypeCatalogMerger.CATALOG_NAME ) )
            {
                // Not use same way as '<parent-path>/maven-metadata.xml' because the archetype filename is not just 'archetype-catalog.xml'. Instead, they look like '.+-archetype-catalog.xml'
                int lastDot = path.lastIndexOf( "." );
                if ( lastDot > 0 )
                {
                    toMergePath = path.substring( 0, lastDot );
                }
                if ( !toMergePath.endsWith( ArchetypeCatalogMerger.CATALOG_NAME ) )
                {
                    logger.error( "Invalid archetype toMergePath, {}", toMergePath ); // should not happen!
                    return null;
                }
            }
            logger.debug( "Generate archetype, toMergePath: {}", toMergePath );

            final List<Transfer> sources = fileManager.retrieveAllRaw( members, toMergePath, new EventMetadata() );
            logger.trace( "Retrieve all raw, sources: {}", sources );

            final byte[] merged = merger.merge( sources, group, toMergePath );
            if ( merged != null )
            {
                Transfer toMergeTarget = fileManager.getTransfer( group, toMergePath );
                try(OutputStream fos = toMergeTarget.openOutputStream( TransferOperation.GENERATE, true, eventMetadata ))
                {
                    fos.write( merged );
                }
                catch ( final IOException e )
                {
                    throw new IndyWorkflowException( "Failed to write merged archetype catalog to: {}.\nError: {}", e, target, e.getMessage() );
                }
            }
        }

        if ( target.exists() )
        {
            return target;
        }

        return null;
    }

    @Override
    public List<StoreResource> generateGroupDirectoryContent( final Group group, final List<ArtifactStore> members,
                                                              final String path, final EventMetadata eventMetadata )
        throws IndyWorkflowException
    {
        final List<StoreResource> result = new ArrayList<>();
        for ( final String filename : HANDLED_FILENAMES )
        {
            StoreResource resource =
                    new StoreResource( LocationUtils.toLocation( group ), Paths.get( path, filename ).toString() );
            Transfer transfer = fileManager.getTransfer( group.getKey(), resource.getPath() );
            if ( transfer != null && transfer.exists( eventMetadata ) )
            {
                result.add( resource );
            }
        }

        return result;
    }

    @Override
    protected String getMergedMetadataName()
    {
        return ArchetypeCatalogMerger.CATALOG_NAME;
    }

}
