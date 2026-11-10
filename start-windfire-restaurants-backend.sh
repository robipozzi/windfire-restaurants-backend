source ./setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=start-windfire-restaurants-backend.sh
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
	# Trusted only for Keycloak calls (spring.ssl.bundle.pem.keycloak); MongoDB Atlas keeps the JVM default trust
	if [ ! -f "$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_ROOT_CA_CERTIFICATE" ]; then
		echo ${red}Error: Windfire Keycloak Root CA not found: $DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_ROOT_CA_CERTIFICATE${end} >&2
		echo ${yel}Create it with windfire-security/keycloak/security/setupTLS.sh${end} >&2
		exit 1
	fi
	inputKeycloakServer
	inputKeycloakServerPort
	export KEYCLOAK_SERVER=$KEYCLOAK_HOST
	export KEYCLOAK_SERVER_PORT=$KEYCLOAK_PORT
	inputCorsAllowedOrigins
	export CORS_ALLOWED_ORIGINS=$CORS_ALLOWED_ORIGINS
	export HOME_DIR=$HOME
	inputMongoDBPassword
	export MONGODB_PASSWORD=$MONGODB_PWD
	java -version
	java -jar target/$JAR_NAME-$VERSION.jar --spring.profiles.active=dev &
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

inputMongoDBPassword()
{
    ###### Set MongoDB password
    echo ${grn}Enter MongoDB dev password \(user windfire-dev-app on windfire-dev.dx6zesz.mongodb.net\) : ${end}
    read -s MONGODB_PWD
}
####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
main