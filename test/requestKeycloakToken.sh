source ../setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=requestKeycloakToken.sh
PLATFORM_OPTION=
FUNCTION=
DEFAULT_REALM_ID=windfire-restaurants
DEFAULT_REALM_USERNAME=windfire
DEFAULT_GRANT_TYPE=password
REALM_ID=
REALM_USERNAME=
REALM_PASSWORD=
GRANT_TYPE=
####################################
###### Variable section - END ######
####################################

######################################
###### Function section - START ######
######################################
main()
{
	printChoosePlatform
	
	inputKeycloakServerEndpoint
	inputRealmId
	inputRealmUsername
	inputRealmPassword
	inputGrantType
	
	echo ${grn}Running curl command to get OAuth2 Access Token ...${end}
	$FUNCTION
}

printChoosePlatform()
{
	echo ${grn}Select Keycloak platform to test: ${end}
    echo "${grn}1. Server on Localhost (No TLS)${end}"
	echo "${grn}2. Server on Localhost (TLS enabled with self signed certificate)${end}"
	read PLATFORM_OPTION
	setFunction
}

setFunction()
{
	case $PLATFORM_OPTION in
		1)  FUNCTION=curlNoTLS
			;;
		2)  FUNCTION=curlSelfSignedTLS
			;;
		*) 	printf "\n${red}No valid option selected${end}\n"
			printChooseFunction
			;;
	esac
}

curlNoTLS()
{
	CURL="curl -X POST http://$KEYCLOAK_HOST:$KEYCLOAK_PORT/realms/$REALM_ID/protocol/openid-connect/token"
	echo ${blu}Running $CURL ...${end}
	$CURL \
		-H "Accept: application/json" \
		-H "Content-Type: application/x-www-form-urlencoded" \
		-H "cache-control: no-cache" \
		-d "client_id=$REALM_ID&username=$REALM_USERNAME&password=$REALM_PASSWORD&grant_type=$GRANT_TYPE"
}

curlSelfSignedTLS()
{
	CURL="curl -X POST https://$KEYCLOAK_HOST:$KEYCLOAK_TLS_SERVER_PORT/realms/$REALM_ID/protocol/openid-connect/token"
	echo ${blu}Running $CURL ...${end}
	
	#####################################################
	##### it calls Keycloak using the following		#####
	##### $HOME/opt/keycloak/ssl/ca_keycloak.pem	#####
	#####################################################
	
	$CURL \
	    --cacert $DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_CACERT_PEM \
		-H "Accept: application/json" \
		-H "Content-Type: application/x-www-form-urlencoded" \
		-H "cache-control: no-cache" \
		-d "client_id=$REALM_ID&username=$REALM_USERNAME&password=$REALM_PASSWORD&grant_type=$GRANT_TYPE"
}

inputKeycloakServerEndpoint()
{
	echo ${cyn}Input Keycloak server host - leaving blank will set Keycloak server host to ${end}${mag}$DEFAULT_KEYCLOAK_HOST : ${end}
	read KEYCLOAK_HOST
	if [ "$KEYCLOAK_HOST" == "" ]; then
		KEYCLOAK_HOST=$DEFAULT_KEYCLOAK_HOST
    fi
    
    echo ${cyn}Input Keycloak server port - leaving blank will set Keycloak server port to ${end}${mag}$DEFAULT_KEYCLOAK_PORT : ${end}
	read KEYCLOAK_PORT
	if [ "$KEYCLOAK_PORT" == "" ]; then
		KEYCLOAK_PORT=$DEFAULT_KEYCLOAK_PORT
    fi
}

inputRealmId()
{
	echo ${cyn}Input Realm ID - leaving blank will set Realm ID to ${end}${mag}$DEFAULT_REALM_ID : ${end}
	read REALM_ID
	if [ "$REALM_ID" == "" ]; then
		REALM_ID=$DEFAULT_REALM_ID
    fi
}

inputRealmUsername()
{
	echo ${cyn}Input Realm Username - leaving blank will set Realm Username to ${end}${mag}$DEFAULT_REALM_USERNAME : ${end}
	read REALM_USERNAME
	if [ "$REALM_USERNAME" == "" ]; then
		REALM_USERNAME=$DEFAULT_REALM_USERNAME
    fi
}

inputRealmPassword()
{
	echo ${cyn}Input Realm Password : ${end}
	read -s REALM_PASSWORD
	setRealmPassword
}

setRealmPassword()
{  
	if [ -z $REALM_PASSWORD ]; then
		echo ${red}No Realm Password input${end}
		inputRealmPassword
	fi
}

inputGrantType()
{
	echo ${cyn}Input Grant Type - leaving blank will set Grant Type to ${end}${mag}$DEFAULT_GRANT_TYPE : ${end}
	read GRANT_TYPE
	if [ "$GRANT_TYPE" == "" ]; then
		GRANT_TYPE=$DEFAULT_GRANT_TYPE
    fi
}
####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
main