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
package org.commonjava.indy.schedule.event;

public class ScheduleTriggerEvent
{

    private final String jobType;

    private final String payload;

    public ScheduleTriggerEvent( final String jobType, final String payload )
    {
        this.jobType = jobType;
        this.payload = payload;
    }

    public String getJobType()
    {
        return jobType;
    }

    public String getPayload()
    {
        return payload;
    }

    @Override
    public String toString()
    {
        return String.format( "SchedulerTriggerEvent [eventType=%s, jobType=%s, payload=%s]", getClass().getName(), jobType, payload );
    }

}
