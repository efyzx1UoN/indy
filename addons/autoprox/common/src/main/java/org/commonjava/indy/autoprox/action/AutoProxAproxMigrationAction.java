/**
 * Copyright (C) 2011 Red Hat, Inc. (jdcasey@commonjava.org)
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
package org.commonjava.indy.autoprox.action;

import org.commonjava.indy.action.IndyLifecycleException;
import org.commonjava.indy.action.MigrationAction;
import org.commonjava.indy.audit.ChangeSummary;
import org.commonjava.indy.autoprox.conf.AutoProxConfig;
import org.commonjava.indy.subsys.datafile.DataFile;
import org.commonjava.indy.subsys.datafile.DataFileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Migrate promotion validation rules from aprox/AProx/Aprox packages and naming to indy/Indy.
 */
public class AutoProxAproxMigrationAction
        implements MigrationAction
{
    @Inject
    private DataFileManager ffManager;

    @Inject
    private AutoProxConfig config;

    @Override
    public boolean migrate()
            throws IndyLifecycleException
    {
        final DataFile dataDir = ffManager.getDataFile( config.getBasedir() );

        Logger logger = LoggerFactory.getLogger( getClass() );
        logger.debug( "Scanning {} for autoprox rules...", dataDir );
        int changed = 0;
        if ( dataDir.exists() )
        {
            final DataFile[] scripts = dataDir.listFiles( ( pathname ) -> {
                logger.trace( "Checking for autoprox rule in: {}", pathname );
                return pathname.getName().endsWith( ".groovy" );
            } );

            logger.info( "Migrating autoprox rules." );
            for ( final DataFile script : scripts )
            {
                try
                {
                    String scriptContent = script.readString();
                    String migrated = scriptContent.replaceAll( "A[Pp]rox", "Indy" ).replaceAll( "aprox", "indy" );
                    if ( !migrated.equals( scriptContent ) )
                    {
                        logger.info( "Migrating autoprox rule in: {}", script.getPath() );
                        script.writeString( migrated, new ChangeSummary( ChangeSummary.SYSTEM_USER,
                                                                         "Migrating to Indy packages / naming" ) );
                        changed++;
                    }
                }
                catch ( IOException e )
                {
                    e.printStackTrace();
                }
            }
        }

        return changed != 0;
    }

    @Override
    public int getMigrationPriority()
    {
        return 90;
    }

    @Override
    public String getId()
    {
        return "aprox-autoprox-rules";
    }
}
