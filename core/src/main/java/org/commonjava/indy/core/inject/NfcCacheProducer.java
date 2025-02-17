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
package org.commonjava.indy.core.inject;

import org.commonjava.indy.subsys.infinispan.BasicCacheHandle;
import org.commonjava.indy.subsys.infinispan.CacheHandle;
import org.commonjava.indy.subsys.infinispan.CacheProducer;
import org.commonjava.indy.subsys.infinispan.config.ISPNRemoteConfiguration;
import org.infinispan.protostream.BaseMarshaller;
import org.infinispan.protostream.MessageMarshaller;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces ISPN Cache instances (wrapped in {@link CacheHandle} to help with shutdown blocking) for use in core class
 * implementations.
 */
public class NfcCacheProducer
{

    private static final String NFC = "nfc";

    @Inject
    private CacheProducer cacheProducer;

    @Inject
    private ISPNRemoteConfiguration remoteConfiguration;

    @NfcCache
    @Produces
    @ApplicationScoped
    public BasicCacheHandle<String, NfcConcreteResourceWrapper> nfcCache()
    {
        if ( remoteConfiguration.isEnabled() )
        {
            List<BaseMarshaller> marshallerList = new ArrayList<>();
            marshallerList.add( new NfcConcreteResourceMarshaller() );
            cacheProducer.registerProtoAndMarshallers( "nfc.proto", marshallerList );
        }
        return cacheProducer.getBasicCache( NFC );
    }
}
