source ./setenv.sh

##### Variable section - START
SCRIPT=run-windfire-restaurants-backend.sh
JAR_NAME=windfire-restaurants-backend
VERSION=0.0.1-SNAPSHOT
MONGODB_PWD=
##### Variable section - END

##### Function section - START
main()
{
	CURRENT_DIRECTORY=$(pwd)
	inputMongoDBPassword
	export MONGODB_PASSWORD=$MONGODB_PWD
	java -version
	java -jar target/$JAR_NAME-$VERSION.jar
	
}

inputMongoDBPassword()
{
    ###### Set MongoDB password
    echo ${grn}Enter MongoDB password : ${end}
    read -s MONGODB_PWD
}
##### Function section - END

# ##############################################
# #################### MAIN ####################
# ##############################################
main