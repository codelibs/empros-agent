/*
 * Copyright 2012-2020 CodeLibs Project and the Others.
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
package org.codelibs.empros.agent.watcher.file;


import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.codelibs.empros.agent.event.Event;
import org.codelibs.empros.agent.event.EventFilter;
import org.codelibs.empros.agent.util.PropertiesUtil;

public class FileEventFilter implements EventFilter{
    private final String FILEWATCHER_PROPERTIES = "filewatcher.properties";

    private final String EXCLUDE_KEY = "excludePath";

    private final String EXCLUDE_NONE_FILEEXTENSION_KEY = "excludeNoneFileExtension";

    private List<String> excludePathList = new ArrayList<String>();

    private boolean excludeNoneFileExtension;

    public FileEventFilter() {
        int count = 1;
        while (true) {
            final String excludePath = PropertiesUtil.getAsString(FILEWATCHER_PROPERTIES, EXCLUDE_KEY + count, null);
            if (StringUtils.isBlank(excludePath)) {
                break;
            }
            excludePathList.add(excludePath);
            count++;
        }

        excludeNoneFileExtension = Boolean.parseBoolean(PropertiesUtil.getAsString(FILEWATCHER_PROPERTIES, EXCLUDE_NONE_FILEEXTENSION_KEY, "false"));
    }

    @Override
    public Event convert(final Event target) {
        boolean ret = true;
        if (excludeNoneFileExtension) {
            ret = filterByExtension(target);
        }
        if (ret && !excludePathList.isEmpty()) {
            ret = filterByExcludePath(target);
        }

        return ret ? target : null;
    }

    private boolean filterByExtension(final Event event) {
        final String path = (String) event.get(FileWatchTask.FILE);
        if (StringUtils.isNotBlank(path) && path.lastIndexOf('/') > 0) {
            final String filename = path.substring(path.lastIndexOf('/') + 1);
            return filename.contains(".");
        }
        return true;
    }

    private boolean filterByExcludePath(final Event event) {
        final String path = (String) event.get(FileWatchTask.FILE);
        if (StringUtils.isNotBlank(path)) {
            for (final String excludePath : excludePathList) {
                final Pattern p = Pattern.compile(excludePath);
                final Matcher m = p.matcher(path);
                if(m.find()) {
                    return false;
                }
            }
        }

        return true;
    }
}
