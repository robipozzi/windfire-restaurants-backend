source ../setenv.sh

##### Variable section - START
SCRIPT=requestKeycloakToken.sh
DEFAULT_KEYCLOAK_SERVER=raspberry01
DEFAULT_KEYCLOAK_PORT=8080
DEFAULT_REALM_ID=windfire-restaurants
DEFAULT_REALM_USERNAME=windfire
DEFAULT_GRANT_TYPE=password
REALM_ID=
REALM_USERNAME=
REALM_PASSWORD=
GRANT_TYPE=
##### Variable section - END

##### Function section - START
main()
{	
	echo ${grn}Running curl command to get OAuth2 Access Token ...${end}
	
	inputKeycloakServerEndpoint
	inputRealmId
	inputRealmUsername
	inputRealmPassword
	inputGrantType
	
	curl -X POST http://$KEYCLOAK_SERVER_ADDRESS:$KEYCLOAK_SERVER_PORT/realms/$REALM_ID/protocol/openid-connect/token \
		-H "Accept: application/json" \
		-H "Content-Type: application/x-www-form-urlencoded" \
		-H "cache-control: no-cache" \
		-d "client_id=$REALM_ID&username=$REALM_USERNAME&password=$REALM_PASSWORD&grant_type=$GRANT_TYPE"
}

inputKeycloakServerEndpoint()
{
	echo ${cyn}Input Keycloak server URL - leaving blank will set Keycloak server URL to ${end}${mag}$DEFAULT_KEYCLOAK_SERVER : ${end}
	read KEYCLOAK_SERVER_ADDRESS
	if [ "$KEYCLOAK_SERVER_ADDRESS" == "" ]; then
		KEYCLOAK_SERVER_ADDRESS=$DEFAULT_KEYCLOAK_SERVER
    fi
    
    echo ${cyn}Input Keycloak server port - leaving blank will set Keycloak server port to ${end}${mag}$DEFAULT_KEYCLOAK_PORT : ${end}
	read KEYCLOAK_SERVER_PORT
	if [ "$KEYCLOAK_SERVER_PORT" == "" ]; then
		KEYCLOAK_SERVER_PORT=$DEFAULT_KEYCLOAK_PORT
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
##### Function section - END

# ##############################################
# #################### MAIN ####################
# ##############################################
main