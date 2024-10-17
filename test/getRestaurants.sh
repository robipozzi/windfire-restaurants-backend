source ../setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=getRestaurants.sh
TEST_CHOICE=
CURL_COMMAND=
RUN_TEST_FUNCTION=
AUTHENTICATION_TOKEN=
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
    echo "${grn}1. Get Restaurants (with no authentication) ${end}"
	echo "${grn}2. Get Restaurants (using an authentication token) ${end}"
	read TEST_CHOICE
	case $TEST_CHOICE in
		1)  RUN_TEST_FUNCTION="getRestaurants"
			;;
        2)  RUN_TEST_FUNCTION="getRestaurantsWithToken"
			;;
		*) 	echo ${red}No valid option selected${end}
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

getRestaurantsWithToken()
{
	echo ${grn}Get Restaurants with HTTP GET method - Authentication Token required ...${end}
	echo
	inputAuthenticationToken
	echo
	CURL="curl -X GET http://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants"
	echo ${blu}Running $CURL ...${end}
	$CURL \
		-H "Accept: application/json" \
		-H "Content-Type: application/json" \
		-H "cache-control: no-cache" \
		-H "Authorization: Bearer $AUTHENTICATION_TOKEN"
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
}

inputAuthenticationToken()
{
	echo ${cyn}Input Authentication Token : ${end}
	read -n 2500 AUTHENTICATION_TOKEN
	if [ -z $AUTHENTICATION_TOKEN ]; then
		echo ${red}No Authentication token input${end}
		inputAuthenticationToken
	fi
}

####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
main