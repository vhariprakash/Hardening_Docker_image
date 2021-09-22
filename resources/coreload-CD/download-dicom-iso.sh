#!/bin/sh

if [ -z "$1" ]; then
    echo "MISSING [Service Name]."
	echo "Usage : ./download-dicom-iso.sh [serviceName]"
    echo "ServiceName: [qido,wado,delete,stow,deidentification-dcm-services,storage-commitment-dcm-services,print,print-service-worker,storage-commitment-service-worker,dicom-web-imaging-service, scheduler-service, mpps, job-manager]"
    exit 1
fi



#default download path will be set to current directory
downloadPath=${2:-.}

echo "Request to download "$1" file..."
while :
do
  case $1 in
        qido)

		  echo "Downloading Nuevo-DCM-Services-QIDO-1.0.0.iso in :"$downloadPath
          	  rm -f $downloadPath/Nuevo-DCM-Services-QIDO-1.0.0.iso
                  wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Nuevo-DCM-Services-QIDO-1.0.0/latest/Nuevo-DCM-Services-QIDO-1.0.0.iso -P $downloadPath
                  exit 0
                  ;;
       stow)
                  echo "Downloading Nuevo-DCM-Services-STOW-1.0.0.iso in :"$downloadPath
                  rm -f $downloadPath/Nuevo-DCM-Services-STOW-1.0.0.iso
                  wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Nuevo-DCM-Services-STOW-1.0.0/latest/Nuevo-DCM-Services-STOW-1.0.0.iso -P $downloadPath
                  exit 0
                  ;;

       wado)
                 echo "Downloading Nuevo-DCM-Services-WADO-1.0.0.iso in :"$downloadPath
                 rm -f $downloadPath/Nuevo-DCM-Services-WADO-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Nuevo-DCM-Services-WADO-1.0.0/latest/Nuevo-DCM-Services-WADO-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;

       delete)
                 echo "Downloading Nuevo-DCM-Services-Deletion-1.0.0.iso in :"$downloadPath
                 rm -f $downloadPath/Nuevo-DCM-Services-Deletion-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Nuevo-DCM-Services-Deletion-1.0.0/latest/Nuevo-DCM-Services-Deletion-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;

       deidentification)
                 echo "Downloading Deidentification-DCM-Services-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/Deidentification-DCM-Services-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Deidentification-DCM-Services-1.0.0/latest/Deidentification-DCM-Services-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;

       print-dcm-services)
                 echo "Downloading Print-DCM-Services-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/Print-DCM-Services-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Print-DCM-Services-1.0.0/latest/Print-DCM-Services-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;

       storage-commitment-dcm-services)
               echo "Downloading Storage-Commitment-Service-Services-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/Storage-Commitment-DCM-Services-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Storage-Commitment-DCM-Services-1.0.0/latest/Storage-Commitment-DCM-Services-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;

        print-service-worker)
               echo "Downloading Print-Service-Worker-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/Print-Service-Worker-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Print-Service-Worker-1.0.0/latest/Print-Service-Worker-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;

        storage-commitment-service-worker)
               echo "Downloading Storage-Commitment-Service-Worker-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/Storage-Commitment-Service-Worker-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Storage-Commitment-Service-Worker-1.0.0/latest/Storage-Commitment-Service-Worker-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;
        dicom-web-imaging-service)
               echo "Downloading Dicom-Web-Imaging-Service-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/Dicom-Web-Imaging-Service-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Dicom-Web-Imaging-Service-1.0.0/latest/Dicom-Web-Imaging-Service-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;
        scheduler-service)
               echo "Downloading Scheduler-Service-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/Scheduler-Service-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Scheduler-Service-1.0.0/latest/Scheduler-Service-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;
        mpps-dcm-services)
               echo "Downloading mpps-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/mpps-dcm-services-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/mpps-dcm-services-1.0.0/latest/mpps-dcm-services-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;
        conductor-task-status-updator)
               echo "Downloading conductor-task-status-updator-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/conductor-task-status-updator-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Conductor-Task-Status-Updator-1.0.0/latest/Conductor-Task-Status-Updator-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;
        conductor-workflow-task-install)
               echo "Downloading conductor-workflow-task-install-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/conductor-workflow-task-install-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Conductor-Workflow-Task-Install-1.0.0/latest/Conductor-Workflow-Task-Install-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;
        workitem-management)
               echo "Downloading workitem-management-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/workitem-management-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/workitem-management-1.0.0/latest/workitem-management-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;
        mpps)
               echo "Downloading mpps-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/mpps-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/mpps-1.0.0/latest/mpps-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;
        job-manager)
               echo "Downloading job-manager-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/job-manager-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/job-manager-1.0.0/latest/job-manager-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;
        uid-dcm-services)
               echo "Downloading uid-dcm-services-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/uid-dcm-services-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/uid-dcm-services-1.0.0/latest/uid-dcm-services-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;
        ping-dcm-services)
               echo "Downloading ping-dcm-services-1.0.0 in :"$downloadPath
                 rm -f $downloadPath/uid-dcm-services-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Ping-DCM-Services-1.0.0/latest/Ping-DCM-Services-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;
        filmer-app)
               echo "Downloading Dicom-Web-Imaging-Filmer-1.0.0.iso in :"$downloadPath
                 rm -f $downloadPath/Dicom-Web-Imaging-Filmer-1.0.0.iso
                 wget https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/generic-coreload-snapshot/Dicom-Web-Imaging-Filmer-1.0.0/latest/Dicom-Web-Imaging-Filmer-1.0.0.iso -P $downloadPath
                 exit 0
                 ;;
       *)
                echo "Sorry," $1" is not valid service Mention one of these:[stow,qido,wado,delete,deidentification]"
                exit 1
                ;;
     esac
done
