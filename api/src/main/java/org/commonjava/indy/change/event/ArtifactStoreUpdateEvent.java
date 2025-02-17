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
package org.commonjava.indy.change.event;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.commonjava.indy.model.core.ArtifactStore;
import org.commonjava.maven.galley.event.EventMetadata;

/**
 * Event signaling that one or more specified {@link ArtifactStore} instances' configurations were changed. The {@link ArtifactStoreUpdateType}
 * gives more information about the nature of the update.
 */
public abstract class ArtifactStoreUpdateEvent
    extends AbstractIndyEvent
{

    private final ArtifactStoreUpdateType type;

    private Map<ArtifactStore, ArtifactStore> changeMap;

    protected ArtifactStoreUpdateEvent( final ArtifactStoreUpdateType type, final EventMetadata eventMetadata,
                                        final Map<ArtifactStore, ArtifactStore> changeMap )
    {
        super( eventMetadata, changeMap.keySet() );
        this.changeMap = cloneOriginals( changeMap );
        this.type = type;
    }

    private Map<ArtifactStore,ArtifactStore> cloneOriginals( Map<ArtifactStore, ArtifactStore> changeMap )
    {
        Map<ArtifactStore, ArtifactStore> cleaned = new HashMap<>();
        changeMap.forEach( (key,value)->{
            if ( key != null && value != null )
            {
                cleaned.put( key, value.copyOf() );
            }
        });

        return cleaned;
    }

    public <T extends ArtifactStore> T getOriginal( T store )
    {
        return (T) changeMap.get( store );
    }

    public Map<ArtifactStore, ArtifactStore> getChangeMap()
    {
        return changeMap;
    }
    /**
     * Return the type of update that took place.
     */
    public ArtifactStoreUpdateType getType()
    {
        return type;
    }

    /**
     * Return the changed {@link ArtifactStore}'s specified in this event.
     */
    public Collection<ArtifactStore> getChanges()
    {
        return getStores();
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{changed=" + getChanges() + ",type=" + type +
                '}';
    }
}
