source ../setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=testRestaurants.sh
TEST_CHOICE=
CURL_COMMAND=
RUN_TEST_FUNCTION=
DOCUMENT_ID
####################################
###### Variable section - END ######
####################################

######################################
###### Function section - START ######
######################################
main()
{
	selectTest
	inputAppServerEndpoint
	$RUN_TEST_FUNCTION
}

selectTest()
{
	echo ${grn}Select test to run : ${end}
    echo "${grn}1. Get Restaurants (No TLS, No authentication) ${end}"
	echo "${grn}2. Get Restaurant by id (No TLS, No authentication) ${end}"
	echo "${grn}3. Create Restaurant (No TLS, No authentication) ${end}"
	echo "${grn}4. Delete Restaurant by id (No TLS, No authentication) ${end}"
	read TEST_CHOICE
	case $TEST_CHOICE in
		1)  RUN_TEST_FUNCTION="getRestaurants"
			;;
        2)  RUN_TEST_FUNCTION="getRestaurantById"
			;;
		3)  RUN_TEST_FUNCTION="createRestaurant"
			;;
		4)  RUN_TEST_FUNCTION="deleteRestaurantById"
			;;
		*) 	echo "${red}No valid option selected${end}"
			selectTest
			;;
	esac
}

getRestaurants()
{
	echo ${grn}Get Restaurants with HTTP GET method - No Authentication ...${end}
	CURL="curl -X GET http://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants"
	echo ${blu}Running $CURL ...${end}
	$CURL \
		-H "Accept: application/json" \
		-H "Content-Type: application/json" \
		-H "cache-control: no-cache"
}

getRestaurantById()
{
	echo ${grn}Get Restaurant by id with HTTP GET method - No Authentication ...${end}
	inputDocumentId
	CURL="curl -X GET http://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants/$DOCUMENT_ID"
	echo ${blu}Running $CURL ...${end}
	$CURL \
		-H "Accept: application/json" \
		-H "Content-Type: application/json" \
		-H "cache-control: no-cache"
}

createRestaurant()
{
	echo ${grn}Create Restaurant with HTTP POST method - No Authentication ...${end}
	CURL="curl -X POST http://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants"
	echo ${blu}Running $CURL ...${end}
	$CURL \
		-H "Accept: application/json" \
		-H "Content-Type: application/json" \
		-H "cache-control: no-cache"
}

deleteRestaurantById()
{
	echo ${grn}Delete Restaurant by id with HTTP DELETE method - No Authentication ...${end}
	inputDocumentId
	CURL="curl -X DELETE http://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants/$DOCUMENT_ID"
	echo ${blu}Running $CURL ...${end}
	$CURL \
		-H "Accept: application/json" \
		-H "Content-Type: application/json" \
		-H "cache-control: no-cache"
}

inputAppServerEndpoint()
{
	echo ${cyn}Input Application server host - leaving blank will set Application server host to ${end}${mag}$DEFAULT_APPLICATION_SERVER_HOST : ${end}
	read APPLICATION_SERVER_HOST
	if [ "$APPLICATION_SERVER_HOST" == "" ]; then
		APPLICATION_SERVER_HOST=$DEFAULT_APPLICATION_SERVER_HOST
    fi
    
    echo ${cyn}Input Application server port - leaving blank will set Application server port to ${end}${mag}$DEFAULT_APPLICATION_SERVER_PORT : ${end}
	read APPLICATION_SERVER_PORT
	if [ "$APPLICATION_SERVER_PORT" == "" ]; then
		APPLICATION_SERVER_PORT=$DEFAULT_APPLICATION_SERVER_PORT
    fi
    
    case $APPLICATION_SERVER_HOST in
		$DEFAULT_APPLICATION_SERVER_HOST) TLS_DIR=$DEFAULT_TLS_DIR
			;;
        "localhost") TLS_DIR=$LOCAL_DEFAULT_TLS_DIR
			;;
		*) 	TLS_DIR=""
			;;
	esac
}

inputDocumentId()
{
	echo ${cyn}Input document id${end}
	read DOCUMENT_ID
}
####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
main