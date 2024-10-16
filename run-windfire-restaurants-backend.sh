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
	java -version
	inputKeystorePassword
	inputMongoDBPassword
	export KEYSTORE_PASSWORD=$KEYSTORE_PWD
	export MONGODB_PASSWORD=$MONGODB_PWD
	java -jar target/$JAR_NAME-$VERSION.jar
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