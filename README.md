#empros-agent
##build
    $ git clone git@github.com:codelibs/empros-agent.git
    $ cd empros-agent
    $ mvn clean package

##Settings
Necessary settings to use filewatcher.  
* agent.properties
    * backupAndRestore
    * backupDirectory
* filewatcher.properties
    * watchPath1
* emprosapi.properties
    * emprosUrl

##Install to Windows Service
Copy "target/empros-agent-1.0.0-SNAPSHOT-jar-with-dependencies.jar" to "package/empros-agent/bin/".  
Copy "src/main/resources/*" to "package/empros-agent/conf/".  
Execute "package/empros-agent/bin/service.bat"
