source ../setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=generate-keycloak-truststore.sh
JAVA_HOME_DETECTED=
JAVA_CACERTS=
KEYCLOAK_TRUSTSTORE_PATH=
KEYCLOAK_CACERT_PEM_PATH=
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
    KEYCLOAK_CACERT_PEM_PATH=$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_CACERT_PEM

    if [ ! -f "$KEYCLOAK_CACERT_PEM_PATH" ]; then
        echo ${red}Error: Keycloak CA certificate not found: $KEYCLOAK_CACERT_PEM_PATH${end} >&2
        echo ${yel}This script only rebuilds the truststore, it does not create the CA certificate.${end} >&2
        echo ${yel}Copy the Keycloak CA cert to that path first, then re-run this script.${end} >&2
        exit 1
    fi

    echo ${cyn}Ensuring truststore directory exists: ${end}${grn}$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR${end}
    mkdir -p "$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR" || { echo ${red}Error: failed to create directory: $DEFAULT_KEYCLOAK_TRUSTSTORE_DIR${end} >&2; exit 1; }
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

    CMD_RUN="keytool -importcert -trustcacerts -noprompt -alias ca_keycloak -file $KEYCLOAK_CACERT_PEM_PATH -keystore $KEYCLOAK_TRUSTSTORE_PATH -storepass $DEFAULT_KEYCLOAK_TRUSTSTORE_PWD"
    echo ${cyn}Importing Keycloak CA certificate using following command:${end}
    echo ${grn}$CMD_RUN${end}
    eval $CMD_RUN || { echo ${red}Error: failed to import Keycloak CA certificate${end} >&2; exit 1; }
    echo
}

verifyTruststore()
{
    echo ${blu}*******************************${end}
    echo ${blu}***** Verify truststore *****${end}
    echo ${blu}*******************************${end}
    echo

    CMD_RUN="keytool -list -keystore $KEYCLOAK_TRUSTSTORE_PATH -storepass $DEFAULT_KEYCLOAK_TRUSTSTORE_PWD | grep -Ei \"ca_keycloak|digicert|amazon|isrg\""
    echo ${cyn}Verifying truststore contents using following command:${end}
    echo ${grn}$CMD_RUN${end}
    eval $CMD_RUN || { echo ${red}Error: truststore verification failed, neither ca_keycloak nor a known public CA was found${end} >&2; exit 1; }
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
    echo ${yel}Reminder: this truststore is wired in JVM-wide, so it trusts the Keycloak CA in addition to,${end}
    echo ${yel}not instead of, the public CAs it was seeded from.${end}
    echo
}
####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
checkPreRequisites
detectJavaCacerts
rebuildTruststore
verifyTruststore
printSummary
