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
    echo "${grn}1. Get Restaurants (No TLS, No authentication) ${end}"
	echo "${grn}2. Get Restaurants (No TLS, using an authentication token) ${end}"
	echo "${grn}3. Get Restaurants (With TLS, No authentication) ${end}"
	echo "${grn}4. Get Restaurants (With TLS, using an authentication token) ${end}"
	read TEST_CHOICE
	case $TEST_CHOICE in
		1)  RUN_TEST_FUNCTION="getRestaurants"
			;;
        2)  RUN_TEST_FUNCTION="getRestaurantsWithToken"
			;;
		3)  RUN_TEST_FUNCTION="getRestaurantsWithTLSNoToken"
			;;
		4)  RUN_TEST_FUNCTION="getRestaurantsWithTokenAndTLS"
			;;
		*) 	printf "\n${red}No valid option selected${end}\n"
			selectTest
			;;
	esac
}

getRestaurants()
{
	echo ${grn}Running curl command to get Restaurants with HTTP GET method - No Authentication ...${end}
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

getRestaurantsWithTLSNoToken()
{
	echo ${grn}Get Restaurants with HTTPS GET method - No Authentication Token required ...${end}
	echo
	CURL="curl -X GET https://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants --cacert $DEFAULT_TRUSTSTORE_DIR/$DEFAULT_PEM_TRUSTSTORE"
	echo ${blu}Running $CURL ...${end}
	
	######################################################################################
	##### it calls SpringBoot app using the following								 #####
	##### $HOME/opt/windfire/ssl/truststore/windfire_restaurants_srvc.truststore.pem #####
	######################################################################################
	
	$CURL \
		-H "Accept: application/json" \
		-H "Content-Type: application/json" \
		-H "cache-control: no-cache" \		
}

getRestaurantsWithTokenAndTLS()
{
	echo ${grn}Get Restaurants with HTTPS GET method - Authentication Token required ...${end}
	echo
	inputAuthenticationToken
	echo
	CURL="curl -X GET https://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants --cacert $DEFAULT_TRUSTSTORE_DIR/$DEFAULT_PEM_TRUSTSTORE"
	echo ${blu}Running $CURL ...${end}
	
	######################################################################################
	##### it calls SpringBoot app using the following								 #####
	##### $HOME/opt/windfire/ssl/truststore/windfire_restaurants_srvc.truststore.pem #####
	######################################################################################
	
	$CURL \
		-H "Accept: application/json" \
		-H "Content-Type: application/json" \
		-H "cache-control: no-cache" \
		-H "Authorization: Bearer $AUTHENTICATION_TOKEN"
}

__getRestaurantsWithTokenAndTLS()
{
	echo ${grn}Getet Restaurants with HTTPS GET method - Authentication Token required ...${end}
	echo
	inputAuthenticationToken
	echo
	CURL="curl -X GET https://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants"
	echo ${blu}Running $CURL ...${end}
	$CURL \
		--cacert $DEFAULT_TRUSTSTORE_DIR/$DEFAULT_PEM_TRUSTSTORE \
		--capath $DEFAULT_TRUSTSTORE_DIR \
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