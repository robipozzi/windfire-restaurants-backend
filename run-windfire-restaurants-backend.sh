source ./setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=run-windfire-restaurants-backend.sh
JAR_NAME=windfire-restaurants-backend
VERSION=0.0.1-SNAPSHOT
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
	# Rebuilds the truststore if it is missing or does not trust the current Windfire Keycloak Root CA
	(cd ssl && ./generate-keycloak-truststore.sh --if-stale) || { echo ${red}Error: Keycloak client truststore is not usable${end} >&2; exit 1; }
	inputKeycloakServer
	inputKeycloakServerPort
	export KEYCLOAK_SERVER=$KEYCLOAK_HOST
	export KEYCLOAK_SERVER_PORT=$KEYCLOAK_PORT
	inputCorsAllowedOrigins
	export CORS_ALLOWED_ORIGINS=$CORS_ALLOWED_ORIGINS
	export HOME_DIR=$HOME
	inputKeycloakTruststorePassword
	export KEYCLOAK_TRUSTSTORE_PWD=$KEYCLOAK_TRUSTSTORE_PWD
	inputMongoDBPassword
	export MONGODB_PASSWORD=$MONGODB_PWD
	java -version
	java -Djavax.net.ssl.trustStore=$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_TRUSTSTORE \
	     -Djavax.net.ssl.trustStorePassword=$KEYCLOAK_TRUSTSTORE_PWD \
	     -jar target/$JAR_NAME-$VERSION.jar --spring.profiles.active=dev &
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

inputCorsAllowedOrigins()
{
	echo ${cyn}Input comma-separated CORS allowed origins - leaving blank will set CORS allowed origins to ${end}${mag}$DEFAULT_CORS_ALLOWED_ORIGINS : ${end}
	read CORS_ALLOWED_ORIGINS
	if [ "$CORS_ALLOWED_ORIGINS" == "" ]; then
		CORS_ALLOWED_ORIGINS=$DEFAULT_CORS_ALLOWED_ORIGINS
    fi
}

inputKeycloakTruststorePassword()
{
	echo ${cyn}Input Keycloak client truststore password - leaving blank will set truststore password to ${end}${mag}[***DEFAULT KEYCLOAK TRUSTSTORE PWD***] : ${end}
	read -s KEYCLOAK_TRUSTSTORE_PWD
	echo
	if [ "$KEYCLOAK_TRUSTSTORE_PWD" == "" ]; then
		KEYCLOAK_TRUSTSTORE_PWD=$DEFAULT_KEYCLOAK_TRUSTSTORE_PWD
    fi
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