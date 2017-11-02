empros-agent
=======

## build

    $ mvn clean package

## Settings

Necessary settings to use filewatcher.

* agent.properties
    * backupAndRestore
    * backupDirectory
* filewatcher.properties
    * watchPath1
* emprosapi.properties
    * emprosUrl/esHost

## Install to Windows Service

1. Copy "target/empros-agent-{version}-SNAPSHOT-jar-with-dependencies.jar" to "package/empros-agent/bin/".
1. Copy "src/main/resources/*" to "package/empros-agent/conf/".
1. Execute "package/empros-agent/bin/service.bat"

## scan mode

Scan all files under the directories that is specified scanPath1 in filescanner.ptoperties.
```
java -cp "./conf:empros-agent-x.x.x-jar-with-dependencies.jar" org.codelibs.empros.agent.Main scan
```
