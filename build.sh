#!/usr/bin/env bash

JAVA_CMD=java
[ -n "$JDK_HOME" ] && JAVA_CMD=$JDK_HOME/bin/java
echo "Using Java command: $JAVA_CMD"

SBT_OPTIONS="-Xmx1G \
    -XX:MaxPermSize=250m \
    -XX:+UseCompressedOops \
    -Dsbt.log.noformat=true \
    -Dbuild.number=$BUILD_NUMBER \
    -Dbuild.vcs.number=$BUILD_VCS_NUMBER"

cd $(dirname $0)
mkdir -p target/artifacts/bonobo

if $JAVA_CMD $SBT_OPTIONS -jar ./sbt-launch.jar assembly
then
    cd target
    cp scala-2.12/bonobo.jar ./artifacts/bonobo/bonobo.jar
    cp ../riff-raff.yaml ./artifacts
    echo "##teamcity[publishArtifacts '$(dirname $0)/target/artifacts => .']"
else
    echo 'Failed to build bonobo'
    exit 1
fi