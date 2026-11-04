source ./setenv.sh

##### Variable section - START
SCRIPT=deploy.sh
PLATFORM_OPTION=
KEYCLOAK_HOST=
KEYCLOAK_PORT=
MONGODB_PWD=
##### Variable section - END

# ***** Function section - START
main()
{
	selectPlatform
}

buildApplication()
{
    echo ${blu}**********************************************${end}
    echo ${blu}***** Build Java SpringBoot Application  *****${end}
    echo ${blu}**********************************************${end}
    echo
    ./build.sh 4
    echo
}

selectPlatform()
{
	echo ${grn}Select deployment platform : ${end}
    echo "${grn}1. Raspberry${end}"
	read PLATFORM_OPTION
	
	case $PLATFORM_OPTION in
		1)  deployRaspberry
			;;
		*)	echo ${red}No valid option selected${end} 	
			selectPlatform
			;;
	esac
}

checkSSLCertificates()
{
    echo ${blu}************************************************${end}
    echo ${blu}***** Check SSL certificates for Raspberry *****${end}
    echo ${blu}************************************************${end}
    echo
    if [ ! -f "$RASPBERRY_CERTS_DIR/$DEFAULT_CERTIFICATE" ] || [ ! -f "$RASPBERRY_KEYSTORE_DIR/$DEFAULT_KEY" ]; then
        echo ${yel}SSL certificate/key not found for raspberry environment. Generating...${end}
        echo ${yel}When prompted, select option 2 \(raspberry\)${end}
        echo
        (cd ssl && ./generate-ssl-cert.sh)
    else
        echo ${grn}SSL certificate found in $RASPBERRY_CERTS_DIR${end}
        echo ${grn}SSL key found in $RASPBERRY_KEYSTORE_DIR${end}
    fi
    echo
}

checkKeycloakRootCA()
{
    echo ${blu}*******************************************${end}
    echo ${blu}***** Check Windfire Keycloak Root CA *****${end}
    echo ${blu}*******************************************${end}
    echo
    # Copied to the Pi and trusted only for Keycloak calls (spring.ssl.bundle.pem.keycloak)
    if [ ! -f "$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_ROOT_CA_CERTIFICATE" ]; then
        echo ${red}Error: Windfire Keycloak Root CA not found: $DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_ROOT_CA_CERTIFICATE${end} >&2
        echo ${yel}Create it with windfire-security/keycloak/security/setupTLS.sh${end} >&2
        exit 1
    fi
    echo ${grn}Windfire Keycloak Root CA found in $DEFAULT_KEYCLOAK_TRUSTSTORE_DIR${end}
    echo
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

inputMongoDBPassword()
{
	MONGODB_PWD=
	while [ "$MONGODB_PWD" == "" ]; do
		echo ${grn}Enter MongoDB prod password \(user windfire-prod-app on windfire.rxdd4.mongodb.net\) : ${end}
		read -s MONGODB_PWD
		echo
		if [ "$MONGODB_PWD" == "" ]; then
			echo ${red}MongoDB password cannot be blank${end}
		fi
	done
}

collectSecrets()
{
	inputKeycloakServer
	inputKeycloakServerPort
	inputMongoDBPassword
	export KEYCLOAK_SERVER=$KEYCLOAK_HOST
	export KEYCLOAK_SERVER_PORT=$KEYCLOAK_PORT
	export MONGODB_PASSWORD=$MONGODB_PWD
}

deployRaspberry()
{
	buildApplication
	checkSSLCertificates
	checkKeycloakRootCA
	collectSecrets
	# sudo on raspberry02 requires pi's password: -K asks for it (BECOME password)
	ansible-playbook -K deployment/raspberry/windfire-restaurants.yaml
}

# ***** Function section - END

# ##############################################
# #################### MAIN ####################
# ##############################################
main