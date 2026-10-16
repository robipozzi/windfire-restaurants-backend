source ../setenv.sh

# Usage: ./generate-keycloak-truststore.sh [--if-stale]
#   --if-stale : rebuild only if the truststore is missing or does not hold the current
#                Windfire Keycloak Root CA (alias windfire-keycloak-root, same SHA-256 fingerprint)

######################################
###### Variable section - START ######
######################################
SCRIPT=generate-keycloak-truststore.sh
JAVA_HOME_DETECTED=
JAVA_CACERTS=
KEYCLOAK_TRUSTSTORE_PATH=
KEYCLOAK_ROOT_CA_CERTIFICATE_PATH=
KEYCLOAK_TRUSTSTORE_ALIAS=windfire-keycloak-root
IF_STALE=
####################################
###### Variable section - END ######
####################################

######################################
###### Function section - START ######
######################################
checkPreRequisites()
{
    echo ${blu}*******************************${end}
    echo ${blu}***** Pre-flight checks *****${end}
    echo ${blu}*******************************${end}
    echo

    if ! command -v java >/dev/null 2>&1; then
        echo ${red}Error: java is required but was not found on the PATH${end} >&2
        exit 1
    fi

    if ! command -v keytool >/dev/null 2>&1; then
        echo ${red}Error: keytool is required but was not found on the PATH${end} >&2
        exit 1
    fi

    KEYCLOAK_TRUSTSTORE_PATH=$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_TRUSTSTORE
    KEYCLOAK_ROOT_CA_CERTIFICATE_PATH=$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_ROOT_CA_CERTIFICATE

    if [ ! -f "$KEYCLOAK_ROOT_CA_CERTIFICATE_PATH" ]; then
        echo ${red}Error: Windfire Keycloak Root CA certificate not found: $KEYCLOAK_ROOT_CA_CERTIFICATE_PATH${end} >&2
        echo ${yel}This script only rebuilds the truststore, it does not create the CA certificate.${end} >&2
        echo ${yel}Create it with windfire-security/keycloak/security/setupTLS.sh option 1, or copy it to that path, then re-run this script.${end} >&2
        exit 1
    fi

    echo ${cyn}Ensuring truststore directory exists: ${end}${grn}$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR${end}
    mkdir -p "$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR" || { echo ${red}Error: failed to create directory: $DEFAULT_KEYCLOAK_TRUSTSTORE_DIR${end} >&2; exit 1; }
    echo
}

checkTruststoreUpToDate()
{
    echo ${blu}*****************************************${end}
    echo ${blu}***** Check truststore is up to date *****${end}
    echo ${blu}*****************************************${end}
    echo

    if [ ! -f "$KEYCLOAK_TRUSTSTORE_PATH" ]; then
        echo ${yel}Truststore not found, it will be built: $KEYCLOAK_TRUSTSTORE_PATH${end}
        echo
        return
    fi

    # Fingerprints are compared as upper case hex without separators, openssl and keytool format them differently
    CA_FINGERPRINT=$(openssl x509 -in "$KEYCLOAK_ROOT_CA_CERTIFICATE_PATH" -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':' | tr '[:lower:]' '[:upper:]')
    TRUSTSTORE_FINGERPRINT=$(keytool -list -v -alias $KEYCLOAK_TRUSTSTORE_ALIAS -keystore "$KEYCLOAK_TRUSTSTORE_PATH" -storepass $DEFAULT_KEYCLOAK_TRUSTSTORE_PWD 2>/dev/null | awk '/SHA256:/ {print $2; exit}' | tr -d ':' | tr '[:lower:]' '[:upper:]')

    if [ -n "$CA_FINGERPRINT" ] && [ "$CA_FINGERPRINT" == "$TRUSTSTORE_FINGERPRINT" ]; then
        echo ${grn}Truststore is up to date, it already trusts the Windfire Keycloak Root CA: $KEYCLOAK_TRUSTSTORE_PATH${end}
        echo
        exit 0
    fi

    echo ${yel}Truststore does not hold the current Windfire Keycloak Root CA, it will be rebuilt: $KEYCLOAK_TRUSTSTORE_PATH${end}
    echo
}

detectJavaCacerts()
{
    echo ${blu}*********************************${end}
    echo ${blu}***** Detect JDK cacerts *****${end}
    echo ${blu}*********************************${end}
    echo

    JAVA_HOME_DETECTED=$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java\.home/ {print $2}' | xargs)
    JAVA_CACERTS=$JAVA_HOME_DETECTED/lib/security/cacerts

    if [ ! -f "$JAVA_CACERTS" ]; then
        echo ${red}Error: JDK cacerts not found: $JAVA_CACERTS${end} >&2
        exit 1
    fi

    echo ${grn}Using JDK cacerts: $JAVA_CACERTS${end}
    echo
}

rebuildTruststore()
{
    echo ${blu}*************************************${end}
    echo ${blu}***** Rebuild Keycloak truststore *****${end}
    echo ${blu}*************************************${end}
    echo

    if [ -f "$KEYCLOAK_TRUSTSTORE_PATH" ]; then
        echo ${yel}Warning: existing truststore will be replaced: $KEYCLOAK_TRUSTSTORE_PATH${end}
    fi

    CMD_RUN="rm -f $KEYCLOAK_TRUSTSTORE_PATH"
    echo ${cyn}Removing any existing truststore using following command:${end}
    echo ${grn}$CMD_RUN${end}
    eval $CMD_RUN || { echo ${red}Error: failed to remove existing truststore${end} >&2; exit 1; }
    echo

    CMD_RUN="cp $JAVA_CACERTS $KEYCLOAK_TRUSTSTORE_PATH"
    echo ${cyn}Seeding truststore from the JDK cacerts using following command:${end}
    echo ${grn}$CMD_RUN${end}
    eval $CMD_RUN || { echo ${red}Error: failed to seed truststore from JDK cacerts${end} >&2; exit 1; }
    echo

    CMD_RUN="chmod u+w $KEYCLOAK_TRUSTSTORE_PATH"
    echo ${cyn}Making truststore writable using following command:${end}
    echo ${grn}$CMD_RUN${end}
    eval $CMD_RUN || { echo ${red}Error: failed to make truststore writable${end} >&2; exit 1; }
    echo

    CMD_RUN="keytool -importcert -trustcacerts -noprompt -alias $KEYCLOAK_TRUSTSTORE_ALIAS -file $KEYCLOAK_ROOT_CA_CERTIFICATE_PATH -keystore $KEYCLOAK_TRUSTSTORE_PATH -storepass $DEFAULT_KEYCLOAK_TRUSTSTORE_PWD"
    echo ${cyn}Importing Windfire Keycloak Root CA certificate using following command:${end}
    echo ${grn}$CMD_RUN${end}
    eval $CMD_RUN || { echo ${red}Error: failed to import Windfire Keycloak Root CA certificate${end} >&2; exit 1; }
    echo
}

verifyTruststore()
{
    echo ${blu}*******************************${end}
    echo ${blu}***** Verify truststore *****${end}
    echo ${blu}*******************************${end}
    echo

    CMD_RUN="keytool -list -keystore $KEYCLOAK_TRUSTSTORE_PATH -storepass $DEFAULT_KEYCLOAK_TRUSTSTORE_PWD | grep -Ei \"windfire-keycloak-root|digicert|amazon|isrg\""
    echo ${cyn}Verifying truststore contents using following command:${end}
    echo ${grn}$CMD_RUN${end}
    eval $CMD_RUN || { echo ${red}Error: truststore verification failed, neither windfire-keycloak-root nor a known public CA was found${end} >&2; exit 1; }
    echo
}

printSummary()
{
    echo ${blu}***************************${end}
    echo ${blu}***** Truststore ready *****${end}
    echo ${blu}***************************${end}
    echo ${grn}Truststore : $KEYCLOAK_TRUSTSTORE_PATH${end}
    echo ${grn}Password   : $DEFAULT_KEYCLOAK_TRUSTSTORE_PWD${end}
    echo
    echo ${yel}Reminder: this truststore is wired in JVM-wide, so it trusts the Windfire Keycloak Root CA in addition to,${end}
    echo ${yel}not instead of, the public CAs it was seeded from.${end}
    echo
}
####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
case "$1" in
    "")         ;;
    --if-stale) IF_STALE=true ;;
    *)          echo ${red}Usage: $SCRIPT [--if-stale]${end} >&2; exit 1 ;;
esac
checkPreRequisites
if [ "$IF_STALE" == "true" ]; then
    checkTruststoreUpToDate
fi
detectJavaCacerts
rebuildTruststore
verifyTruststore
printSummary
