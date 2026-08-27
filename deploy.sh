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

deployRaspberry()
{
	buildApplication
	checkSSLCertificates
	ansible-playbook deployment/raspberry/windfire-restaurants.yaml
}	

# ***** Function section - END

# ##############################################
# #################### MAIN ####################
# ##############################################
main