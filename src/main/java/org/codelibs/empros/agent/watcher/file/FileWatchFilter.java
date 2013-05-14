package org.codelibs.empros.agent.watcher.file;


import org.apache.commons.lang.StringUtils;
import org.codelibs.empros.agent.event.Event;
import org.codelibs.empros.agent.util.PropertiesUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileWatchFilter {
    private final String FILEWATCHER_PROPERTIES = "filewatcher.properties";

    private final String EXCLUDE_KEY = "excludePath";

    private final String EXCLUDE_NONE_FILEEXTENSION_KEY = "excludeNoneFileExtension";

    private List<String> excludePathList = new ArrayList<String>();

    private boolean excludeNoneFileExtension;

    public FileWatchFilter() {
        int count = 1;
        while (true) {
            String excludePath = PropertiesUtil.getAsString(FILEWATCHER_PROPERTIES, EXCLUDE_KEY + count, null);
            if (StringUtils.isBlank(excludePath)) {
                break;
            }
            excludePathList.add(excludePath);
            count++;
        }

        excludeNoneFileExtension = Boolean.parseBoolean(PropertiesUtil.getAsString(FILEWATCHER_PROPERTIES, EXCLUDE_NONE_FILEEXTENSION_KEY, "false"));
    }

    public boolean filter(Event event) {
        boolean ret = true;

        if (excludeNoneFileExtension) {
            ret = filterByExtension(event);
        }

        if (ret && excludePathList.size() > 0) {
            ret = filterByExcludePath(event);
        }

        return ret;
    }

    private boolean filterByExtension(Event event) {
        boolean ret = true;
        String path = (String) event.get(FileWatchTask.FILE);
        if (StringUtils.isNotBlank(path)) {
            if (path.lastIndexOf("/") > 0) {
                String filename = path.substring(path.lastIndexOf("/") + 1);
                if (!filename.contains(".")) {
                    ret = false;
                }
            }
        }
        return ret;
    }

    private boolean filterByExcludePath(Event event) {
        boolean ret = true;
        String path = (String) event.get(FileWatchTask.FILE);
        if (StringUtils.isNotBlank(path)) {
            for (String excludePath : excludePathList) {
                Pattern p = Pattern.compile(excludePath);
                Matcher m = p.matcher(path);
                if(m.find()) {
                    ret = false;
                    break;
                }
            }
        }

        return ret;
    }
}
