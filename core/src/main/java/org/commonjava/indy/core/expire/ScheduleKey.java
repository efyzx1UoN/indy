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
package org.commonjava.indy.core.expire;

import org.commonjava.indy.model.core.StoreKey;
import org.commonjava.indy.model.core.StoreType;
import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Objects;

@Indexed
public class ScheduleKey implements Externalizable, Serializable
{
    @Field( index = Index.YES, analyze = Analyze.NO )
    private StoreKey storeKey;

    @Field( index = Index.YES, analyze = Analyze.NO )
    private String type;

    @Field( index = Index.YES, analyze = Analyze.NO )
    private String name;

    @Field( index = Index.YES, analyze = Analyze.NO )
    private String groupName;

    public ScheduleKey()
    {
    }

    public ScheduleKey( final StoreKey storeKey, final String type, final String name )
    {
        this.storeKey = storeKey;
        this.type = type;
        this.name = name;
        this.groupName = ScheduleManagerUtils.groupName( this.storeKey, this.type );
    }

    public StoreKey getStoreKey()
    {
        return storeKey;
    }

    public String getType()
    {
        return type;
    }

    public String getName()
    {
        return name;
    }

    public String getGroupName()
    {
        return groupName;
    }

    public static ScheduleKey fromGroupWithName( final String group, final String name )
    {
        final String[] splits = group.split( "#" );
        return new ScheduleKey( StoreKey.fromString( splits[0] ), splits[1], name );
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( !( obj instanceof ScheduleKey ) )
        {
            return false;
        }

        final ScheduleKey that = (ScheduleKey) obj;
        return Objects.equals( this.storeKey, that.storeKey ) && Objects.equals( this.type, that.type )
                && Objects.equals( this.name, that.name );
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( storeKey == null ) ? 0 : storeKey.hashCode() );
        result = prime * result + ( ( type == null ) ? 0 : type.hashCode() );
        result = prime * result + ( ( name == null ) ? 0 : name.hashCode() );
        return result;
    }

    public String toStringKey()
    {
        return ( storeKey != null ? storeKey.toString() : "" ) + "#" + type + "#" + name;
    }

    public String toString()
    {
        return toStringKey();
    }

    public boolean exists()
    {
        return this.storeKey != null && this.type != null;
    }

    @Override
    public void writeExternal( ObjectOutput out )
            throws IOException
    {
        out.writeObject( storeKey.getName() );
        out.writeObject( storeKey.getType().name() );
        out.writeObject( storeKey.getPackageType() );
        out.writeObject( type );
        out.writeObject( name );
    }

    @Override
    public void readExternal( ObjectInput in )
            throws IOException, ClassNotFoundException
    {
        final String storeKeyName = (String) in.readObject();
        final StoreType storeType = StoreType.get( (String) in.readObject() );
        final String packageType = (String)in.readObject();
        storeKey = new StoreKey( packageType, storeType, storeKeyName );

        final String typeStr = (String) in.readObject();
        type = "".equals( typeStr ) ? null : typeStr;

        final String nameStr = (String) in.readObject();
        name = "".equals( nameStr ) ? null : nameStr;

        groupName = ScheduleManagerUtils.groupName( storeKey, type );
    }
}
