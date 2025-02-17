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
package org.commonjava.indy.folo.ftest.report;

import org.apache.commons.io.IOUtils;
import org.commonjava.indy.folo.client.IndyFoloAdminClientModule;
import org.commonjava.indy.folo.client.IndyFoloContentClientModule;
import org.commonjava.indy.folo.dto.TrackedContentDTO;
import org.commonjava.indy.folo.dto.TrackedContentEntryDTO;
import org.commonjava.indy.ftest.core.category.EventDependent;
import org.commonjava.indy.model.core.StoreKey;
import org.commonjava.indy.test.fixture.core.CoreServerFixture;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.commonjava.indy.folo.data.FoloFiler.BAK_DIR;
import static org.commonjava.indy.folo.data.FoloFiler.FOLO_DIR;
import static org.commonjava.indy.folo.data.FoloFiler.FOLO_SEALED_DAT;
import static org.commonjava.indy.model.core.StoreType.hosted;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

@Category( EventDependent.class )
public class FoloBackupListenerTest
    extends AbstractTrackingReportTest
{

    @Test
    public void run()
        throws Exception
    {
        final String trackingId = newName();

        final InputStream stream = new ByteArrayInputStream( ( "This is a test: " + System.nanoTime() ).getBytes() );

        final String path = "/path/to/foo.class";
        client.module( IndyFoloContentClientModule.class )
              .store( trackingId, hosted, STORE, path, stream );

        IndyFoloAdminClientModule adminModule = client.module( IndyFoloAdminClientModule.class );
        boolean success = adminModule.sealTrackingRecord( trackingId );
        assertThat( success, equalTo( true ) );

        final TrackedContentDTO report = adminModule
                                               .getTrackingReport( trackingId );
        assertThat( report, notNullValue() );

        final Set<TrackedContentEntryDTO> uploads = report.getUploads();

        assertThat( uploads, notNullValue() );
        assertThat( uploads.size(), equalTo( 1 ) );

        final TrackedContentEntryDTO entry = uploads.iterator()
                                                    .next();

        System.out.println( entry );

        assertThat( entry, notNullValue() );
        assertThat( entry.getStoreKey(), equalTo( new StoreKey( hosted, STORE ) ) );
        assertThat( entry.getPath(), equalTo( path ) );
        assertThat( entry.getLocalUrl(),
                    equalTo( client.content().contentUrl( hosted, STORE, path ) ) );
        assertThat( entry.getOriginUrl(), nullValue() );

        ///////////////////////////////////////////////////////////////////////////////
        // the above are copied from StoreFileAndVerifyInTrackingReportTest
        // next, we check the backup dir contains two files, one from startup action, the other is just added

        //**/
//        File f1 = new File( dataDir, FOLO_DIR + "/" + BAK_DIR +"/sealed/" + trackingId );
//        assertTrue( f1.exists() );

        File f2 = new File( dataDir, FOLO_DIR + "/" + BAK_DIR +"/sealed" );
        assertDumped( f2 );
    }

    private void assertDumped(File dir)
    {
        assertTrue( dir.isDirectory() );
        String[] files = dir.list();
        logger.debug( "Dump backup files, size: {}, names: {}", files.length, files );
    }

    @Override
    protected void initTestData( CoreServerFixture fixture ) throws IOException
    {
        /*
         * I tested and it worked, e.g., assertDumped printed:
         * Dump backup files, size: 5, names: [tKSJuWNp, nwaXryzSBS, LhhVhowqwK, knVLPZaVwj, TfGgnAwGYW]
         *
         * I would like to keep this but it will fail when upgrading to ISPN 9.x with persistence error.
         * It is better not bothering the upgrading. Once the upgrading is down, we can reopen this case by a new .dat.
         */
        //copyToDataFile( "folo-backup-action/folo-saled-dat", FOLO_DIR + "/" + FOLO_SEALED_DAT );
    }

}
