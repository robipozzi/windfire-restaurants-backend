source ./setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=run-windfire-restaurants-backend.sh
JAR_NAME=windfire-restaurants-backend
VERSION=0.0.1-SNAPSHOT
KEYSTORE_PWD=
MONGODB_PWD=
####################################
###### Variable section - END ######
####################################

######################################
###### Function section - START ######
######################################
main()
{
	CURRENT_DIRECTORY=$(pwd)
	inputKeycloakServer
	inputKeycloakServerPort
	export KEYCLOAK_SERVER=$KEYCLOAK_HOST
	export KEYCLOAK_SERVER_PORT=$KEYCLOAK_PORT
	inputKeystorePassword
	inputMongoDBPassword
	export KEYSTORE_PASSWORD=$KEYSTORE_PWD
	export MONGODB_PASSWORD=$MONGODB_PWD
  java -version
	java -jar target/$JAR_NAME-$VERSION.jar
}

inputKeycloakServer()
{
	echo ${cyn}Input Keycloak server host - leaving blank will set Keycloak server host to ${end}${mag}$DEFAULT_KEYCLOAK_HOST : ${end}
	read KEYCLOAK_HOST
	if [ "$KEYCLOAK_HOST" == "" ]; then
		KEYCLOAK_HOST=$DEFAULT_KEYCLOAK_HOST
    fi
}

inputKeycloakServerPort()
{
	echo ${cyn}Input Keycloak server port - leaving blank will set Keycloak server port to ${end}${mag}$DEFAULT_KEYCLOAK_PORT : ${end}
	read KEYCLOAK_PORT
	if [ "$KEYCLOAK_PORT" == "" ]; then
		KEYCLOAK_PORT=$DEFAULT_KEYCLOAK_PORT
    fi
}

inputKeystorePassword()
{
    ###### Set keystore password
    echo ${grn}Enter keystore password : ${end}
    read -s KEYSTORE_PWD
}

inputMongoDBPassword()
{
    ###### Set MongoDB password
    echo ${grn}Enter MongoDB password : ${end}
    read -s MONGODB_PWD
}
####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
main