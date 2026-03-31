source ../setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=setupSSL.sh
KEYSTORE_DIR=
####################################
###### Variable section - END ######
####################################

######################################
###### Function section - START ######
######################################
selectHost()
{
    echo ${blu}******************************${end}
    echo ${blu}***** Select target host *****${end}
    echo ${blu}******************************${end}
    echo
    echo "Choose the target host:"
    echo "  1) localhost"
    echo "  2) raspberry"
    echo
    read -p "Enter choice [1-2]: " HOST_CHOICE
    case $HOST_CHOICE in
        1)
            TARGET_HOST=localhost
            SAN_EXTENSIONS="dns:localhost,ip:127.0.0.1"
            KEYSTORE_DIR=$LOCAL_DEFAULT_TLS_DIR/keystore
            ;;
        2)
            TARGET_HOST=raspberry02
            SAN_EXTENSIONS="dns:raspberry02"
            KEYSTORE_DIR=$DEFAULT_TLS_DIR/keystore
            ;;
        *)
            echo ${red}Invalid choice. Exiting.${end}
            exit 1
            ;;
    esac
    echo ${grn}Selected host: $TARGET_HOST${end}
    echo
}

generateSelfSignedCertificate()
{
    echo ${blu}*************************************************************${end}
    echo ${blu}***** Generate self-signed keystore with SAN extensions *****${end}
    echo ${blu}*************************************************************${end}
    echo

    mkdir -p $KEYSTORE_DIR

    CMD_RUN="keytool -genkeypair \
    			-keyalg RSA -keysize 2048 \
    			-validity $DEFAULT_VALIDITY \
    			-keystore $KEYSTORE_DIR/$DEFAULT_KEYSTORE \
    			-alias $DEFAULT_KEYSTORE_ALIAS \
    			-dname \"CN=$TARGET_HOST, OU=Windfire Restaurants, O=Windfire, L=Milano, ST=Lombardia, C=IT\" \
    			-ext \"SAN=$SAN_EXTENSIONS\""
    echo ${cyn}Generating self-signed keystore using following command:${end}
    echo ${grn}$CMD_RUN${end}
    eval $CMD_RUN
    echo
}

exportCertificate()
{
    echo ${blu}****************************************************${end}
    echo ${blu}***** Export certificate in DER and PEM format *****${end}
    echo ${blu}****************************************************${end}
    echo

    CMD_DER="keytool -exportcert \
    			-keystore $KEYSTORE_DIR/$DEFAULT_KEYSTORE \
    			-alias $DEFAULT_KEYSTORE_ALIAS \
    			-file $KEYSTORE_DIR/$DEFAULT_SERVER_CERTIFICATE"
    echo ${cyn}Exporting certificate to DER format using following command:${end}
    echo ${grn}$CMD_DER${end}
    eval $CMD_DER
    echo

    CMD_PEM="openssl x509 -inform der \
    			-in  $KEYSTORE_DIR/$DEFAULT_SERVER_CERTIFICATE \
    			-out $KEYSTORE_DIR/windfire_restaurants_srvc.pem"
    echo ${cyn}Converting certificate to PEM format using following command:${end}
    echo ${grn}$CMD_PEM${end}
    eval $CMD_PEM
    echo
}

printSummary()
{
    echo ${blu}***************************${end}
    echo ${blu}***** Files generated *****${end}
    echo ${blu}***************************${end}
    echo ${grn}Keystore : $KEYSTORE_DIR/$DEFAULT_KEYSTORE${end}
    echo ${grn}Cert DER : $KEYSTORE_DIR/$DEFAULT_SERVER_CERTIFICATE${end}
    echo ${grn}Cert PEM : $KEYSTORE_DIR/windfire_restaurants_srvc.pem${end}
    echo
    echo ${blu}**************************************************${end}
    echo ${blu}***** Import certificate into macOS Keychain *****${end}
    echo ${blu}**************************************************${end}
    echo ${yel}Running the following command to trust the certificate in Safari:${end}
    echo ${grn}sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain $KEYSTORE_DIR/windfire_restaurants_srvc.pem${end}
    sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain $KEYSTORE_DIR/windfire_restaurants_srvc.pem
    echo
    echo ${yel}Then restart Safari for the change to take effect.${end}
    echo
}
####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
selectHost
generateSelfSignedCertificate
exportCertificate
printSummary