source ../setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=getRestaurants.sh
TEST_CHOICE=
CURL_COMMAND=
RUN_TEST_FUNCTION=
AUTHENTICATION_TOKEN=
TLS_DIR=
TRUSTSTORE_DIR=
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
	inputTlsDir
	echo ${grn}Get Restaurants with HTTPS GET method - No Authentication Token required ...${end}
	echo
	CURL="curl -X GET https://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants --cacert $TRUSTSTORE_DIR/$DEFAULT_PEM_TRUSTSTORE"
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
	inputTlsDir
	echo ${grn}Get Restaurants with HTTPS GET method - Authentication Token required ...${end}
	echo
	inputAuthenticationToken
	echo
	CURL="curl -X GET https://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants --cacert $TRUSTSTORE_DIR/$DEFAULT_PEM_TRUSTSTORE"
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
	inputTlsDir
	echo ${grn}Get Restaurants with HTTPS GET method - Authentication Token required ...${end}
	echo
	inputAuthenticationToken
	echo
	CURL="curl -X GET https://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants"
	echo ${blu}Running $CURL ...${end}
	$CURL \
		--cacert $TRUSTSTORE_DIR/$DEFAULT_PEM_TRUSTSTORE \
		--capath $TRUSTSTORE_DIR \
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

inputTlsDir()
{
    ###### Set TLS dir
    if [ "$TLS_DIR" != "" ]; then
        echo TLS folder is set to $TLS_DIR
    else
        echo ${grn}Enter TLS folder - leaving blank will set it to ${end}${mag}$DEFAULT_TLS_DIR : ${end}
        read TLS_DIR
        if [ "$TLS_DIR" == "" ]; then
            TLS_DIR=$DEFAULT_TLS_DIR
        fi
    fi
    TRUSTSTORE_DIR=$TLS_DIR/truststore
}
####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
main