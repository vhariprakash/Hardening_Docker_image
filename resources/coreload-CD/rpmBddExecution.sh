#!/bin/bash
set -x
componentTestDir=$1
bddTarName=$2
bddCommand=$3
rpmPreStepFile=$4
rpmPostStepFile=$5
cd $componentTestDir
echo "present directory: "
pwd

## --------------- pre bdd step shell script execution -------------------
echo "----------------- Pre Bdd Step shell script execution --------------------"
if [ -f $rpmPreStepFile ]
then
	chmod 755 $rpmPreStepFile
	bash $rpmPreStepFile
	if [ $? != 0 ]
	then
		exit 1
	fi
fi

## --------------- Executing component test execution command -------------------
echo "----------------- Executing component test execution command --------------------"
$bddCommand
if [ $? != 0 ]
then
    echo "BDD COMPONENT TEST EXECCUTION FAILED!!"
    exit 1
fi

## --------------- Reports to be pushed to BDD publisher node / artifactory -------------------
echo "----------------- Reports to be pushed to BDD publisher node / artifactory --------------------"
if [ -d target ]
then
	echo "create tar file : rpm-bdd-${bddTarName}"
	rm -f $componentTestDir/${bddTarName}
	tar -czvf  $componentTestDir/${bddTarName}  $componentTestDir/target/reports/*
    if [ $? != 0 ]
    then
    echo "Error: tar packaging failed"
    exit 1
    fi
else
	echo "RPM BDD report not availalbe !!"
fi

## --------------- post bdd step shell script execution -------------------
echo "----------------- Post Bdd Step shell script execution --------------------"
if [ -f $rpmPostStepFile ]
then
	chmod 755 $rpmPostStepFile
	bash $rpmPostStepFile
	if [ $? != 0 ]
	then
		exit 1
	fi
fi
