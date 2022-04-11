package org.codelibs.empros.agent.watcher.file;

public class PathReplaceRule {
    private final String oldPath;
    private final String newPath;

    public PathReplaceRule(String oldPath, String newPath) {
        this.oldPath = oldPath;
        this.newPath = newPath;
    }

    public String getOldPath() {
        return oldPath;
    }

    public String getNewPath() {
        return newPath;
    }
}
