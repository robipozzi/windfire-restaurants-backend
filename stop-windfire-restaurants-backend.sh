source ./setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=stop-windfire-restaurants-backend.sh
JAR_NAME=windfire-restaurants-backend
WAIT_SECONDS=10
####################################
###### Variable section - END ######
####################################

######################################
###### Function section - START ######
######################################
main()
{
	PIDS=$(pgrep -f "$JAR_NAME")
	if [ -z "$PIDS" ]; then
		echo "${cyn}No running $JAR_NAME process found - nothing to stop${end}"
		exit 0
	fi

	echo "${grn}Stopping $JAR_NAME (PID(s): $PIDS)...${end}"
	kill $PIDS

	waitForShutdown
}

waitForShutdown()
{
	COUNT=0
	while pgrep -f "$JAR_NAME" > /dev/null && [ $COUNT -lt $WAIT_SECONDS ]; do
		sleep 1
		COUNT=$((COUNT + 1))
	done

	REMAINING_PIDS=$(pgrep -f "$JAR_NAME")
	if [ -n "$REMAINING_PIDS" ]; then
		echo "${red}$JAR_NAME did not stop gracefully after ${WAIT_SECONDS}s - force killing (PID(s): $REMAINING_PIDS)...${end}"
		kill -9 $REMAINING_PIDS
		echo "${grn}$JAR_NAME force killed${end}"
	else
		echo "${grn}$JAR_NAME stopped successfully${end}"
	fi
}
####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
main
