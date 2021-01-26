#!/bin/bash

./clean.sh
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
