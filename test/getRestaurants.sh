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

selectTest()
{
	echo ${grn}Select test to run : ${end}
    echo "${grn}1. Get Restaurants (with no authentication) ${end}"
	echo "${grn}2. Get Restaurants (using an authentication token) ${end}"
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
		*) 	printf "\n${red}No valid option selected${end}\n"
			selectTest
			;;
	esac
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