source ../setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=getRestaurants.sh
TEST_CHOICE=
CURL_COMMAND=
RUN_TEST_FUNCTION=
AUTHENTICATION_TOKEN=
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
	CURL="curl -X GET https://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants"
	### *******************************************
	### ***************** WARNING *****************
	### *******************************************
	### The Windfire Root CA must be imported in the keychain (see ssl/generate-ssl-cert.sh)
	### If it is not, a --cacert directive pointing to the Root CA should be added to the curl command
	#CURL="curl -X GET https://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants --cacert $TRUSTSTORE_DIR/$DEFAULT_ROOT_CA_CERTIFICATE -k"
	echo ${blu}Running $CURL ...${end}

	######################################################################################
	##### it calls SpringBoot app trusting the Windfire Root CA in the following	 #####
	##### $DEFAULT_ROOT_CA_TRUSTSTORE_DIR/$DEFAULT_ROOT_CA_CERTIFICATE				 #####
	######################################################################################

	$CURL \
		-H "Accept: application/json" \
		-H "Content-Type: application/json" \
		-H "cache-control: no-cache"
}

getRestaurantsWithTokenAndTLS()
{
	inputTlsDir
	echo ${grn}Get Restaurants with HTTPS GET method - Authentication Token required ...${end}
	echo
	inputAuthenticationToken
	echo
	CURL="curl -X GET https://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants"
	### *******************************************
	### ***************** WARNING *****************
	### *******************************************
	### The Windfire Root CA must be imported in the keychain (see ssl/generate-ssl-cert.sh)
	### If it is not, a --cacert directive pointing to the Root CA should be added to the curl command
	#CURL="curl -X GET https://$APPLICATION_SERVER_HOST:$APPLICATION_SERVER_PORT/restaurants --cacert $TRUSTSTORE_DIR/$DEFAULT_ROOT_CA_CERTIFICATE -k"
	echo ${blu}Running $CURL ...${end}

	######################################################################################
	##### it calls SpringBoot app trusting the Windfire Root CA in the following	 #####
	##### $DEFAULT_ROOT_CA_TRUSTSTORE_DIR/$DEFAULT_ROOT_CA_CERTIFICATE				 #####
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
		--cacert $TRUSTSTORE_DIR/$DEFAULT_ROOT_CA_CERTIFICATE \
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

inputTlsDir()
{
    ###### Set Root CA truststore dir - the same Windfire Root CA signs every server
    ###### certificate (localhost and raspberry alike), so a single truststore is enough
    echo ${grn}Enter Root CA truststore folder - leaving blank will set it to ${end}${mag}$DEFAULT_ROOT_CA_TRUSTSTORE_DIR : ${end}
    read INPUT_TRUSTSTORE_DIR
    if [ "$INPUT_TRUSTSTORE_DIR" == "" ]; then
    	INPUT_TRUSTSTORE_DIR=$DEFAULT_ROOT_CA_TRUSTSTORE_DIR
    fi
    TRUSTSTORE_DIR=$INPUT_TRUSTSTORE_DIR
    echo ${blu}Truststore folder is set to $TRUSTSTORE_DIR${end}
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