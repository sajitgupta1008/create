#!/bin/bash

set -e

echo "Entry Point"

APP_LIB=/usr/src/app/lib
APP_CLASSPATH=$APP_LIB/*

appid=$(echo $MESOS_TASK_ID |  cut -d. -f1)
taskid=$(echo $MESOS_TASK_ID |  cut -d. -f2)
export taskid
export appid

JAVA_OPTS=""
JMX_CONFIG=""
PLAY_SECRET=none
CONFIG="-Dplay.crypto.secret=$PLAY_SECRET"
PLAY_SERVER_START="play.core.server.ProdServerStart"

exec java -cp "$APP_CLASSPATH" $JAVA_OPTS $JMX_CONFIG $CONFIG $PLAY_SERVER_START
