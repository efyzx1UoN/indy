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
package org.commonjava.indy.promote.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Batcher
{
    public static <T> Collection<Collection<T>> batch( Collection<T> collection, int batchSize )
    {
        Collection<Collection<T>> batches = new ArrayList<>();
        List<T> batch = new ArrayList<>( batchSize );
        int count = 0;
        for ( T t : collection )
        {
            batch.add( t );
            count++;
            if ( count >= batchSize )
            {
                batches.add( batch );
                batch = new ArrayList<>( batchSize );
                count = 0;
            }
        }
        if ( !batch.isEmpty() )
        {
            batches.add( batch ); // first or last batch
        }
        return batches;
    }

    /**
     * Calculate the batch size. If transfers size is less than pool size, return 1 (no batch). Or return size/core. e.g.
     * if core is 40, size is 100, return 2; if size is 1000, return 1000/40 = 25.
     */
    public static int getParalleledBatchSize( int size, int corePoolSize )
    {
        if ( size < corePoolSize || corePoolSize <= 0)
        {
            return 1;
        }
        return size / corePoolSize;
    }

}
