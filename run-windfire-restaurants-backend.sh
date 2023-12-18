source ./setenv.sh

##### Variable section - START
SCRIPT=run-windfire-restaurants-backend.sh
JAR_NAME=windfire-restaurants-backend
VERSION=0.0.1-SNAPSHOT
##### Variable section - END

##### Function section - START
main()
{
	CURRENT_DIRECTORY=$(pwd)
	java -version
	java -jar target/$JAR_NAME-$VERSION.jar
	
}
##### Function section - END

# ##############################################
# #################### MAIN ####################
# ##############################################
main