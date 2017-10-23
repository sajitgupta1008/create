#!/bin/bash

set -e

echo "Entry Point"
APP_LIB=/usr/src/app/lib
APP_CLASSPATH=$APP_LIB/*
APP_CLUSTER="guest-accounts-password-application"
CINNAMON_JAR=/usr/src/app/lib/com.lightbend.cinnamon-cinnamon-agent-2.4.0.jar
PLAY_SECRET=none

#CONFIG="-Dplay.crypto.secret=$PLAY_SECRET -Dlagom.cluster.join-self=on"
CONFIG="-Dplay.crypto.secret=$PLAY_SECRET -Dplay.akka.actor-system=$APP_CLUSTER"

PLAY_SERVER_START="play.core.server.ProdServerStart"
appid=$(echo $MESOS_TASK_ID |  cut -d. -f1)
taskid=$(echo $MESOS_TASK_ID |  cut -d. -f2)
export taskid
export appid

exec java -cp "$APP_CLASSPATH" $JAVA_OPTS $JMX_CONFIG -javaagent:$CINNAMON_JAR  $CONFIG $PLAY_SERVER_START
