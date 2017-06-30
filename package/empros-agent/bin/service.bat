cd %~dp0
cd ..
set "EMPROSAGENT_HOME=%cd%"
cd bin
prunsrv.exe //IS//EmprosAgent ^
--DisplayName="Empros Agent" ^
--Description="empros agent" ^
--Install="%EMPROSAGENT_HOME%\bin\prunsrv.exe" ^
--Jvm=auto ^
--Classpath="%EMPROSAGENT_HOME%\conf\;%EMPROSAGENT_HOME%\bin\empros-agent-1.0.2-jar-with-dependencies.jar" ^
--LogPath="%EMPROSAGENT_HOME%\logs" ^
--StdOutput=auto ^
--StdError=auto ^
--StartClass="org.codelibs.empros.agent.Main" ^
--StartMode=jvm ^
--StartParams=start ^
--StartPath="%EMPROSAGENT_HOME%\bin" ^
--StopClass="org.codelibs.empros.agent.Main" ^
--StopMode=jvm ^
--StopParams=stop ^
--StopPath="%EMPROSAGENT_HOME%\bin"
pause
