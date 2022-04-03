#!/bin/bash

cd $(dirname $0) || exit 1
base_dir=$(pwd)
work_dir="$base_dir/workspace"

if [ ! -e "$base_dir"/openjdk-11_windows-x64_bin.zip ]; then
    echo "Download JDK11"
    cd "$base_dir" || exit 1
    wget https://download.java.net/java/ga/jdk11/openjdk-11_windows-x64_bin.zip
fi

echo "Build empros agent."
cd ../
agent_dir=$(pwd)
mvn clean package
agent_version=$(mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -v '^\[INFO\]' | tail -n 1)
echo "Version: $agent_version"

cd "$base_dir" || exit 1
if [ -e "$work_dir" ]; then
    rm -rf "$base_dir/workspace"
fi
mkdir -p "$work_dir/empros-agent"
cd "$work_dir" || exit 1

echo "Create package."
echo "$agent_version" > "$work_dir/empros-agent/Version"
cp -r "$base_dir"/config "$work_dir"/empros-agent/

if [ ! -e "$base_dir"/distribution/empros-agent-service.exe ]; then
    echo "Download winsw."
    wget https://github.com/winsw/winsw/releases/download/v2.11.0/WinSW-x64.exe
    mv "$work_dir"/WinSW-x64.exe "$base_dir"/distribution/empros-agent-service.exe
fi
cp "$base_dir"/distribution/* "$work_dir"/empros-agent/
cp "$agent_dir"/target/empros-agent-"${agent_version}"-jar-with-dependencies.jar "$work_dir"/empros-agent/empros-agent-jar-with-dependencies.jar
unzip -d "$work_dir"/empros-agent/ "$base_dir"/openjdk-11_windows-x64_bin.zip

echo "Archive package."
if [ -e "$base_dir"/empros-agent.zip ]; then
    rm -f "$base_dir"/empros-agent.zip
fi
zip -r "$base_dir"/empros-agent.zip ./empros-agent/*
