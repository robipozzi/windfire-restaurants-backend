source ../setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=generate-ssl-cert.sh
COUNTRY="IT"
REGION="Lombardia"
LOCALITY="Milano"
ORGANIZATION="Windfire"
ORGANIZATIONAL_UNIT="Windfire Restaurants"
TARGET_HOST=
SAN_EXTENSIONS=
CERTS_DIR=
KEYSTORE_DIR=
CERTIFICATE_NAME=
KEY_NAME=
ROOT_CA_CERTIFICATE_NAME=
ROOT_CA_CERTIFICATE_PATH=
ROOT_CA_KEY_PATH=
CSR_FILE=
OPENSSL_EXT_FILE=
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
    echo "Choose the target environment:"
    echo "  1) localhost"
    echo "  2) raspberry"
    echo
    read -p "Enter choice [1-2]: " HOST_CHOICE
    case $HOST_CHOICE in
        1)
            TARGET_HOST=localhost
            SAN_EXTENSIONS=$'DNS.1 = localhost\nIP.1 = 127.0.0.1'
            CERTS_DIR=$LOCALHOST_CERTS_DIR
            KEYSTORE_DIR=$LOCALHOST_KEYSTORE_DIR
            ;;
        2)
            TARGET_HOST=raspberry02
            SAN_EXTENSIONS="DNS.1 = raspberry02"
            CERTS_DIR=$RASPBERRY_CERTS_DIR
            KEYSTORE_DIR=$RASPBERRY_KEYSTORE_DIR
            ;;
        *)
            echo ${red}Invalid choice. Exiting.${end}
            exit 1
            ;;
    esac
    echo ${grn}Selected environment: $TARGET_HOST${end}
    echo
}

checkPreRequisites()
{
    echo ${blu}*******************************${end}
    echo ${blu}***** Pre-flight checks *****${end}
    echo ${blu}*******************************${end}
    echo

    if ! command -v openssl >/dev/null 2>&1; then
        echo ${red}Error: openssl is required but was not found on the PATH${end} >&2
        exit 1
    fi

    echo ${cyn}Ensuring certificate directory exists: ${end}${grn}$CERTS_DIR${end}
    mkdir -p "$CERTS_DIR" || { echo ${red}Error: failed to create directory: $CERTS_DIR${end} >&2; exit 1; }

    echo ${cyn}Ensuring key directory exists: ${end}${grn}$KEYSTORE_DIR${end}
    mkdir -p "$KEYSTORE_DIR" || { echo ${red}Error: failed to create directory: $KEYSTORE_DIR${end} >&2; exit 1; }
    echo
}

inputCertificateName()
{
    read -p "Enter certificate file name [$DEFAULT_CERTIFICATE]: " INPUT_CERTIFICATE_NAME
    if [ "$INPUT_CERTIFICATE_NAME" == "" ]; then
        CERTIFICATE_NAME=$DEFAULT_CERTIFICATE
    else
        CERTIFICATE_NAME=$INPUT_CERTIFICATE_NAME
    fi
    echo ${grn}Certificate file name set to $CERTIFICATE_NAME${end}
    echo
}

inputKeyName()
{
    read -p "Enter key file name [$DEFAULT_KEY]: " INPUT_KEY_NAME
    if [ "$INPUT_KEY_NAME" == "" ]; then
        KEY_NAME=$DEFAULT_KEY
    else
        KEY_NAME=$INPUT_KEY_NAME
    fi
    echo ${grn}Key file name set to $KEY_NAME${end}
    echo
}

inputRootCAName()
{
    read -p "Enter Root CA certificate name [$DEFAULT_ROOT_CA_CERTIFICATE]: " INPUT_ROOT_CA_NAME
    if [ "$INPUT_ROOT_CA_NAME" == "" ]; then
        ROOT_CA_CERTIFICATE_NAME=$DEFAULT_ROOT_CA_CERTIFICATE
    else
        ROOT_CA_CERTIFICATE_NAME=$INPUT_ROOT_CA_NAME
    fi
    ROOT_CA_CERTIFICATE_PATH=$DEFAULT_ROOT_CA_TRUSTSTORE_DIR/$ROOT_CA_CERTIFICATE_NAME
    # The Root CA key is expected to sit alongside the certificate name, in the keystore
    # directory, with the same base name and a .key extension (as produced by createRootCA.sh)
    ROOT_CA_KEY_PATH=$DEFAULT_ROOT_CA_KEYSTORE_DIR/${ROOT_CA_CERTIFICATE_NAME%.*}.key
    echo ${grn}Root CA certificate set to $ROOT_CA_CERTIFICATE_PATH${end}
    echo
}

checkRootCA()
{
    echo ${blu}********************************${end}
    echo ${blu}***** Check Root CA exists *****${end}
    echo ${blu}********************************${end}
    echo

    if [ ! -f "$ROOT_CA_CERTIFICATE_PATH" ] || [ ! -f "$ROOT_CA_KEY_PATH" ]; then
        echo ${red}Error: Windfire Root CA not found${end} >&2
        echo ${red}  expected certificate: $ROOT_CA_CERTIFICATE_PATH${end} >&2
        echo ${red}  expected key        : $ROOT_CA_KEY_PATH${end} >&2
        echo ${yel}Run windfire-security/ssl/createRootCA.sh first to generate the Root CA.${end} >&2
        exit 1
    fi
    echo ${grn}Root CA certificate and key found${end}
    echo
}

createServerPrivateKey()
{
    echo ${blu}***********************************${end}
    echo ${blu}***** Generate server private key *****${end}
    echo ${blu}***********************************${end}
    echo

    CMD_RUN="openssl genrsa -out $KEYSTORE_DIR/$KEY_NAME 2048"
    echo ${cyn}Generating server private key using following command:${end}
    echo ${grn}$CMD_RUN${end}
    eval $CMD_RUN || { echo ${red}Error: failed to generate server private key${end} >&2; exit 1; }
    echo
}

createServerCsr()
{
    echo ${blu}*****************************${end}
    echo ${blu}***** Generate server CSR *****${end}
    echo ${blu}*****************************${end}
    echo

    CSR_FILE=$(mktemp -t generate-ssl-cert).csr
    SUBJECT="/C=$COUNTRY/ST=$REGION/L=$LOCALITY/O=$ORGANIZATION/OU=$ORGANIZATIONAL_UNIT/CN=$TARGET_HOST"
    CMD_RUN="openssl req -new -key $KEYSTORE_DIR/$KEY_NAME -out $CSR_FILE -subj \"$SUBJECT\""
    echo ${cyn}Generating server CSR using following command:${end}
    echo ${grn}$CMD_RUN${end}
    eval $CMD_RUN || { echo ${red}Error: failed to generate server CSR${end} >&2; exit 1; }
    echo
}

signServerCertificate()
{
    echo ${blu}*****************************************************${end}
    echo ${blu}***** Sign server certificate with Windfire Root CA *****${end}
    echo ${blu}*****************************************************${end}
    echo

    OPENSSL_EXT_FILE=$(mktemp -t generate-ssl-cert).ext
    {
        echo "basicConstraints = CA:FALSE"
        echo "keyUsage = digitalSignature, keyEncipherment"
        echo "extendedKeyUsage = serverAuth"
        echo "subjectAltName = @alt_names"
        echo
        echo "[alt_names]"
        echo "$SAN_EXTENSIONS"
    } > "$OPENSSL_EXT_FILE"

    CMD_RUN="openssl x509 -req -in $CSR_FILE \
    			-CA $ROOT_CA_CERTIFICATE_PATH -CAkey $ROOT_CA_KEY_PATH -CAcreateserial \
    			-out $CERTS_DIR/$CERTIFICATE_NAME -days $DEFAULT_VALIDITY -sha256 \
    			-extfile $OPENSSL_EXT_FILE"
    echo ${cyn}Signing server certificate using following command:${end}
    echo ${grn}$CMD_RUN${end}
    if ! eval $CMD_RUN; then
        echo ${red}Error: failed to sign server certificate${end} >&2
        rm -f "$CERTS_DIR/$CERTIFICATE_NAME"
        exit 1
    fi
    echo
}

cleanup()
{
    rm -f "$CSR_FILE" "$OPENSSL_EXT_FILE"
}

printSummary()
{
    echo ${blu}***************************${end}
    echo ${blu}***** Files generated *****${end}
    echo ${blu}***************************${end}
    echo ${grn}Key         : $KEYSTORE_DIR/$KEY_NAME${end}
    echo ${grn}Certificate : $CERTS_DIR/$CERTIFICATE_NAME${end}
    echo ${grn}Signed by   : $ROOT_CA_CERTIFICATE_PATH${end}
    echo
    echo ${blu}**************************************************${end}
    echo ${blu}***** Import Root CA into macOS Keychain *****${end}
    echo ${blu}**************************************************${end}
    echo "${yel}Trusting the Windfire Root CA (only needed once, it covers every certificate it signs):${end}"
    echo ${grn}sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain $ROOT_CA_CERTIFICATE_PATH${end}
    echo
    echo ${yel}Then restart your browser for the change to take effect.${end}
    echo
    sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain $ROOT_CA_CERTIFICATE_PATH
    echo
}
####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
selectHost
checkPreRequisites
inputCertificateName
inputKeyName
inputRootCAName
checkRootCA
createServerPrivateKey
createServerCsr
signServerCertificate
cleanup
printSummary
