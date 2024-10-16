source ./setenv.sh

##### Variable section - START
SCRIPT=deploy.sh
PLATFORM_OPTION=
##### Variable section - END

# ***** Function section - START
main()
{
	selectPlatform
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

deployRaspberry()
{
	ansible-playbook deployment/raspberry/windfire-restaurants.yaml
}	

# ***** Function section - END

# ##############################################
# #################### MAIN ####################
# ##############################################
main