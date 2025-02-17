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
package org.commonjava.indy.action;

/**
 * Performs some sort of service/subsystem bootup just before Indy is preparing to run.
 */
public interface BootupAction
    extends IndyLifecycleAction
{

    /**
     * Initialize some subsystem before migration actions start.
     */
    void init()
        throws IndyLifecycleException;

    /**
     * Used to sort the actions, with highest priority executing first.
     * Priority should generally be between 1-100.
     */
    int getBootPriority();

}
