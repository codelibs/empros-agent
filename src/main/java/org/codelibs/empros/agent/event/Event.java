/*
 * Copyright 2013 the CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.empros.agent.event;

import java.io.Serializable;
import java.util.HashMap;

public class Event extends HashMap<String, Object> implements Serializable {

    private static final long serialVersionUID = 1L;

    private EventComparator eventComparator;

    @Override
    public boolean equals(final Object o) {
        return equals(o, true);
    }

    public boolean equals(final Object o, final boolean useComparator) {
        if (useComparator && eventComparator != null) {
            return eventComparator.equals(this, o);
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return hashCode(true);
    }

    public int hashCode(final boolean useComparator) {
        if (useComparator && eventComparator != null) {
            return eventComparator.hashCode(this);
        }
        return super.hashCode();
    }

    public interface EventComparator {
        boolean equals(Event self, Object target);

        int hashCode(Event self);
    }

    public void setEventComparator(final EventComparator eventComparator) {
        this.eventComparator = eventComparator;
    }
}
