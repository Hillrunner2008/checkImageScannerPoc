#!/bin/bash
opt_xmx=400m
if [[ -z "$JAVA_OPTS" ]]; then
JAVA_OPTS="-Djava.awt.headless=true -Djava.net.preferIPv4Stack=true -Xmx${opt_xmx}"
fi
if [[ $DEBUG_JAVA == "true" ]]; then
	JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y"
fi
java $JAVA_OPTS -jar app.jar 
