#!bin/bash

deployFileName=$1

kubectl create -f $deployFileName
rc=$?

if [ $rc == 0 ];
   then
   echo "BDD pod deployed"

else
  echo "Problem deploying BDD pod"
  exit 1

fi
