#!/bin/bash
# exit when any command fails
set -e

#clean .class files created previously
./clean.sh

if [ $? -eq 0 ]
then
  echo "[OK] Old .class files deleted."
else
  echo "[ERROR] Unable to delete old .class files." >&2
  exit 1
fi

#compile the project with all the external libraries used
javac -cp ../json-simple-1.1.jar \
      -d . \
           Server/AdvKey.java \
	   Server/Card.java \
	   Server/Project.java \
	   Server/ServerInterface.java \
	   Server/ServerImpl.java \
	   Server/ServerMain.java \
	   Client/ClientNotifyInterface.java \
	   Client/MulticastInfos.java \
	   Client/ClientNotifyImpl.java \
	   Client/ClientMain.java

if [ $? -eq 0 ]
then
  echo "[OK] Successfully compiled!"
  #exit 0
else
  echo "[ERROR] Unable to compile the project." >&2
  #exit 1
fi
