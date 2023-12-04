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
PROFILE_OPTION=$1
MONGODB_PWD=
TRUSTSTORE=$2
TRUSTSTORE_PWD=
CONTAINER_IMAGE_NAME=windfire-restaurants-backend
CONTAINER_IMAGE_VERSION=1.0
CONTAINER_NAME=windfire-restaurants-backend
DOCKER_HUB_ID=robipozzi
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