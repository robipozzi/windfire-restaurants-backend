source ../setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=setupSSL.sh
KEYSTORE_DIR=$LOCAL_DEFAULT_TLS_DIR/keystore
####################################
###### Variable section - END ######
####################################

######################################
###### Function section - START ######
######################################
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
    			-dname \"CN=localhost, OU=Windfire, O=Windfire, C=IT\" \
    			-ext \"SAN=dns:localhost,dns:raspberry02,ip:127.0.0.1\""
    echo ${cyn}Generating self-signed keystore using following command:${end}
    echo ${grn}$CMD_RUN${end}
    eval $CMD_RUN
    echo
}

exportCertificate()
{
    echo ${blu}***************************************************************${end}
    echo ${blu}***** Export certificate in DER and PEM format             *****${end}
    echo ${blu}***************************************************************${end}
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
    echo ${blu}***************************************************************${end}
    echo ${blu}***** Files generated                                      *****${end}
    echo ${blu}***************************************************************${end}
    echo ${grn}Keystore : $KEYSTORE_DIR/$DEFAULT_KEYSTORE${end}
    echo ${grn}Cert DER : $KEYSTORE_DIR/$DEFAULT_SERVER_CERTIFICATE${end}
    echo ${grn}Cert PEM : $KEYSTORE_DIR/windfire_restaurants_srvc.pem${end}
    echo
    echo ${blu}***************************************************************${end}
    echo ${blu}***** Import certificate into macOS Keychain               *****${end}
    echo ${blu}***************************************************************${end}
    echo ${yel}Run the following command to trust the certificate in Safari:${end}
    echo ${grn}sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain $KEYSTORE_DIR/windfire_restaurants_srvc.pem${end}
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
generateSelfSignedCertificate
exportCertificate
printSummary