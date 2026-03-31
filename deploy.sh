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

checkSSLCertificates()
{
    echo ${blu}************************************************${end}
    echo ${blu}***** Check SSL certificates for Raspberry *****${end}
    echo ${blu}************************************************${end}
    echo
    local TLS_KEYSTORE_DIR=$DEFAULT_TLS_DIR/keystore
    if [ ! -d "$TLS_KEYSTORE_DIR" ] || [ ! -f "$TLS_KEYSTORE_DIR/$DEFAULT_KEYSTORE" ]; then
        echo ${yel}SSL keystore not found in $TLS_KEYSTORE_DIR. Generating...${end}
        echo
        mkdir -p $TLS_KEYSTORE_DIR
        (cd ssl && ./setupSSL.sh)
    else
        echo ${grn}SSL certificates found in $TLS_KEYSTORE_DIR${end}
    fi
    echo
}

deployRaspberry()
{
	checkSSLCertificates
	ansible-playbook deployment/raspberry/windfire-restaurants.yaml
}	

# ***** Function section - END

# ##############################################
# #################### MAIN ####################
# ##############################################
main