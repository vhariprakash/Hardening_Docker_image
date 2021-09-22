#!bin/bash

deployFileName=$1

kubectl delete -f $deployFileName
rc=$?

if [ $rc == 0 ];
   then
   echo "BDD pod delete"
else
  echo "Problem deleting BDD pod"
fi
