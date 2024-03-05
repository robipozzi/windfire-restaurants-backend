source ../setenv.sh

##### Variable section - START
SCRIPT=getRestaurants.sh
TEST_CHOICE=
CURL_COMMAND=
RUN_TEST_FUNCTION=
AUTHENTICATION_TOKEN=
##### Variable section - END

##### Function section - START
main()
{
	selectTest
	$RUN_TEST_FUNCTION
}

selectTest()
{
	echo ${grn}Select test to run : ${end}
    echo "${grn}1. Get Restaurants (No TLS, No authentication) ${end}"
	echo "${grn}2. Get Restaurants (No TLS, using an authentication token) ${end}"
	echo "${grn}3. Get Restaurants (With TLS, using an authentication token) ${end}"
	echo "${grn}4. Just for test (With TLS, using an authentication token) ${end}"
	read TEST_CHOICE
	setTest
}

setTest()
{
	case $TEST_CHOICE in
		1)  RUN_TEST_FUNCTION="getRestaurants"
			;;
        2)  RUN_TEST_FUNCTION="getRestaurantsWithToken"
			;;
		3)  RUN_TEST_FUNCTION="getRestaurantsWithTokenAndTLS"
			;;
		4)  RUN_TEST_FUNCTION="__getRestaurantsWithTokenAndTLS"
			;;
		*) 	printf "\n${red}No valid option selected${end}\n"
			selectTest
			;;
	esac
}

getRestaurants()
{
	echo ${grn}Running curl command to get Restaurants with HTTP GET method - No Authentication ...${end}
	
	curl -X GET http://$APPLICATION_SERVER_ADDRESS:$APPLICATION_SERVER_PORT/restaurants \
		-H "Accept: application/json" \
		-H "Content-Type: application/json" \
		-H "cache-control: no-cache"
}

getRestaurantsWithToken()
{
	echo ${grn}Running curl command to get Restaurants with HTTP GET method - Authentication Token required ...${end}
	inputAuthenticationToken
	curl -X GET http://$APPLICATION_SERVER_ADDRESS:$APPLICATION_SERVER_PORT/restaurants \
		-H "Accept: application/json" \
		-H "Content-Type: application/json" \
		-H "cache-control: no-cache" \
		-H "Authorization: Bearer $AUTHENTICATION_TOKEN"
}

getRestaurantsWithTokenAndTLS()
{
	echo ${grn}Running curl command to get Restaurants with HTTPS GET method - Authentication Token required ...${end}
	inputAuthenticationToken
	curl -X GET https://$APPLICATION_SERVER_ADDRESS:$APPLICATION_SERVER_PORT/restaurants \
		--cacert $DEFAULT_TRUSTSTORE_DIR/$DEFAULT_CACERT_PEM \
		-H "Accept: application/json" \
		-H "Content-Type: application/json" \
		-H "cache-control: no-cache" \
		-H "Authorization: Bearer $AUTHENTICATION_TOKEN"
}

__getRestaurantsWithTokenAndTLS()
{
	echo ${grn}Running curl command to get Restaurants with HTTPS GET method - Authentication Token required ...${end}
	inputAuthenticationToken
	curl -X GET https://$APPLICATION_SERVER_ADDRESS:$APPLICATION_SERVER_PORT/restaurants \
		--cacert $DEFAULT_TRUSTSTORE_DIR/$DEFAULT_PEM_TRUSTSTORE \
		--capath $DEFAULT_TRUSTSTORE_DIR \
		-H "Accept: application/json" \
		-H "Content-Type: application/json" \
		-H "cache-control: no-cache" \
		-H "Authorization: Bearer $AUTHENTICATION_TOKEN"
}

inputAuthenticationToken()
{
	echo ${cyn}Input Authentication Token : ${end}
	read -n 2500 AUTHENTICATION_TOKEN
	setAuthenticationToken
}

setAuthenticationToken()
{  
	if [ -z $AUTHENTICATION_TOKEN ]; then
		echo ${red}No Authentication token input${end}
		inputAuthenticationToken
	fi
}


##### Function section - END

# ##############################################
# #################### MAIN ####################
# ##############################################
main