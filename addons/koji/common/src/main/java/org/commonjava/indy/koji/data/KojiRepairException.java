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
package org.commonjava.indy.koji.data;

import org.commonjava.indy.IndyException;
import org.commonjava.indy.model.core.StoreKey;

public class KojiRepairException
    extends IndyException
{

    private static final long serialVersionUID = 1L;

    public KojiRepairException( final String message, final Object... params )
    {
        super( message, params );
    }

    public KojiRepairException( final String message, final Throwable cause, final Object... params )
    {
        super( message, cause, params );
    }

    // store key this exception is correlated
    private StoreKey storeKey;

    public StoreKey getStoreKey()
    {
        return storeKey;
    }

    public void setStoreKey( StoreKey storeKey )
    {
        this.storeKey = storeKey;
    }
}
