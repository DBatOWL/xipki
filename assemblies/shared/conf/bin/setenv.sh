export JAVA_OPTS="${JAVA_OPTS} -DXIPKI_BASE=${CATALINA_HOME}/xipki"
export JDK_JAVA_OPTIONS="${JDK_JAVA_OPTIONS} --add-exports=jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED"
