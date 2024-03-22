###################################
##### Terminal Colors - START #####
###################################
red=$'\e[1;31m'
grn=$'\e[1;32m'
yel=$'\e[1;33m'
blu=$'\e[1;34m'
mag=$'\e[1;35m'
cyn=$'\e[1;36m'
end=$'\e[0m'
coffee=$'\xE2\x98\x95'
coffee3="${coffee} ${coffee} ${coffee}"
#################################
##### Terminal Colors - END #####
#################################

######################################
###### Variable section - START ######
######################################
CURRENT_DIRECTORY=
KEYCLOAK_SERVER_ADDRESS=localhost
KEYCLOAK_SERVER_PORT=8080
KEYCLOAK_TLS_SERVER_PORT=8443
APPLICATION_SERVER_ADDRESS=localhost
APPLICATION_SERVER_PORT=8081
# ************************************
# ***** Spring Boot TLS settings *****
# ************************************
# Keystore and CSR settings
DEFAULT_TLS_DIR=$HOME/dev/workspaces/windfire/windfire-restaurants-backend/src/main/resources/keystore
DEFAULT_KEYSTORE=windfire_restaurants_srvc.keystore.jks
DEFAULT_KEYSTORE_ALIAS=windfire_restaurants_srvc
DEFAULT_CSR=windfire_restaurants_srvc.csr
DEFAULT_VALIDITY=1000

# Certificate authority settings
DEFAULT_CA_KEYSTORE=ca_windfire.jks
DEFAULT_CA_ALIAS=ca_windfire
DEFAULT_CACERT=ca_windfire.cer
DEFAULT_CACERT_PEM=ca_windfire.pem


# Signed certificate settings
DEFAULT_SERVER_CERTIFICATE=windfire_restaurants_srvc.cer

# Truststore settings
DEFAULT_TRUSTSTORE_DIR=$HOME/opt/windfire/ssl/truststore
DEFAULT_TRUSTSTORE=windfire_restaurants_srvc.truststore.jks
DEFAULT_PKCS12_TRUSTSTORE=windfire_restaurants_srvc.truststore.p12
DEFAULT_PEM_TRUSTSTORE=windfire_restaurants_srvc.truststore.pem

# *********************************
# ***** Keycloak TLS settings *****
# *********************************
# Keycloak Truststore settings
DEFAULT_KEYCLOAK_TRUSTSTORE_DIR=$HOME/opt/keycloak/ssl/truststore
DEFAULT_KEYCLOAK_CACERT_PEM=keycloak.truststore.pem
####################################
###### Variable section - END ######
####################################