/*******************************************************************************
 * Copyright (c) 2014 Red Hat, Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.commonjava.aprox.mem.data;

import org.commonjava.aprox.audit.BasicSecuritySystem;
import org.commonjava.aprox.audit.SecuritySystem;
import org.commonjava.aprox.core.data.TCKFixtureProvider;
import org.commonjava.aprox.data.StoreDataManager;

public class MemoryTCKFixtureProvider
    implements TCKFixtureProvider
{

    private final MemoryStoreDataManager dataManager = new MemoryStoreDataManager();

    private final SecuritySystem securitySystem = new BasicSecuritySystem();

    @Override
    public StoreDataManager getDataManager()
    {
        return dataManager;
    }

    @Override
    public SecuritySystem getSecuritySystem()
    {
        return securitySystem;
    }

}
