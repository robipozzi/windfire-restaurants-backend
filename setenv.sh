##### Terminal Colors - START
red=$'\e[1;31m'
grn=$'\e[1;32m'
yel=$'\e[1;33m'
blu=$'\e[1;34m'
mag=$'\e[1;35m'
cyn=$'\e[1;36m'
end=$'\e[0m'
coffee=$'\xE2\x98\x95'
coffee3="${coffee} ${coffee} ${coffee}"
##### Terminal Colors - END

###### Variable section - START
CURRENT_DIRECTORY=
KEYCLOAK_SERVER_ADDRESS=localhost
KEYCLOAK_SERVER_PORT=8080
APPLICATION_SERVER_ADDRESS=localhost
APPLICATION_SERVER_PORT=8081
### SSL settings
# Keystore and CSR settings
DEFAULT_SSL_DIR=$HOME/dev/workspaces/windfire/windfire-restaurants-backend/src/main/resources/keystore
DEFAULT_KEYSTORE=windfire_restaurants_srvc.keystore.jks
DEFAULT_KEYSTORE_ALIAS=windfire_restaurants_srvc
DEFAULT_CSR=windfire_restaurants_srvc.csr
DEFAULT_VALIDITY=1000

# Certificate authority settings
DEFAULT_CA_KEYSTORE=ca_windfire.jks
DEFAULT_CA_ALIAS=ca_windfire
DEFAULT_CACERT=ca_windfire.crt
DEFAULT_CACERT_PEM=ca_windfire.pem

# Signed certificate settings
DEFAULT_SERVER_CERTIFICATE=windfire_restaurants_srvc.crt

# Truststore settings
DEFAULT_TRUSTSTORE_DIR=$HOME/opt/windfire/ssl
DEFAULT_TRUSTSTORE=windfire_restaurants_srvc.truststore.jks
DEFAULT_PKCS12_TRUSTSTORE=windfire_restaurants_srvc.truststore.p12
DEFAULT_PEM_TRUSTSTORE=windfire_restaurants_srvc.truststore.pem
###### Variable section - END

##### Function section - START
printProfile()
{
	echo ${grn}Select run platform : ${end}
    echo "${grn}1. Localhost${end}"
    echo "${grn}2. Localhost (SSL enabled)${end}"
	read PROFILE_OPTION
	setProfile
}

setProfile()
{
	case $PROFILE_OPTION in
		1)  printf "\n${grn}Application is running on localhost, going with default profile ...${end}\n" 
			;;
		2)  printf "\n${grn}Application is running on localhost with SSL enabled, going with ssl profile ...${end}\n" 
			;;
		*) 	printf "\n${red}No valid option selected${end}\n"
			printProfile
			;;
	esac
}

inputMongoDbPassword()
{
    ###### Set MongoDb password
    echo ${grn}Enter MongoDb password : ${end}
    read -s MONGODB_PWD
}

inputTruststorePassword()
{
    ###### Set truststore password
    echo ${grn}Enter truststore password : ${end}
    read -s TRUSTSTORE_PWD
}
##### Function section - END