@Library(['eis_pipeline', 'dsl-lib']) _
echo 'Promote ISO'    
def releaseSystem = 'eis-devops'

def build_id = "${env.BUILD_ID}"

node ("${releaseSystem}"){
           def basedir = pwd()
         try{
            stage ('Approval') {
			 def approver_repo_branch = "${env.devopsdslbranch}"
			 def approval_project_folder_name= 'eis'
			 approval {
				approverRepoBranch = "${approver_repo_branch}"
				gitCredsId = 'root-user-docker06'
				approverRepoSshUrl = 'git@gitlab-gxp.cloud.health.ge.com:Edison-Imaging-Service/jenkins-pipeline-approvers.git'
				approvalMessage = 'Approve the build'
				approvalProjectFolderName = "${approval_project_folder_name}"
				approvalProjectKey = 'Promote_ISO'
			 }
	 	 }
		   def devops_deploy_branch = "${params.EESDeployBranch}"?:'master'
		   def artifactory_instance = "${params.artifactoryInstance}"?:"${env.artifactory_url}"
		   def from_artifactory_repo = "${params.fromArtifactoryGenericRepo}"?:'generic-eis-snapshot-all'
		   def to_artifactory_repo = "${params.toArtifactoryGenericRepo}"?:'generic-eis-release'
		   
            
            
		     timestamps {
             step([$class: 'WsCleanup'])
             }	
			 stage ( "Checkout devops-deploy repo code from ${devops_deploy_branch} branch") {
		      // Checkout devops-deploy repo from  branch
			   checkoutDevopsDeployCode("${devops_deploy_branch}")
			 }
			 stage ( 'Promote ISO images to Artifactory release repo') {
			   //promote ISO images
			   //def iso_catalog_yaml_file_parse = readYaml file: 'ISO-Catalogue.yaml'
               promoteIsoImages("${artifactory_instance}","${from_artifactory_repo}","${to_artifactory_repo}")
			   //promoteIsoImages('PackageMetadata',"${artifactory_instance}","${from_artifactory_repo}","${to_artifactory_repo}")
			 }
			 
			 /*stage ( "Update ISO Manifest File in ${devops_deploy_branch} of devops-deploy repo") {
			   //update the ISO manifest file
			   //updateIsoManifest("${devops_deploy_branch}")
			} */
         
        } catch(err){
            echo 'Failed to promote ISO images  :'+ err
            throw err
        }		 
    }	
	
def promoteIsoImages(artifactory_instance,from_artifactory_repo,to_artifactory_repo) {
    def iso_catalog_yaml_file_parse = readYaml file: 'ISO-Catalogue.yaml'
     iso_catalog_yaml_file_parse['ISOs'].keySet().each {
         promoteFunc("${artifactory_instance}","${from_artifactory_repo}","${to_artifactory_repo}","${it}")
     } 
     iso_catalog_yaml_file_parse['PackageMetadata'].keySet().each {
         promoteFunc("${artifactory_instance}","${from_artifactory_repo}","${to_artifactory_repo}","${it}")
     } 
	   
	  if (fileExists('ISO-promotion-failure.txt')) {
		def fileContents = readFile('ISO-promotion-failure.txt')
		ansiColor('xterm') {
		  println " ################################################"
	      println " PROMOTION FAILURE SUMMARY REPORT"
		  println " ################################################"
		} 
		fileContents.split('\n').each {
		  ansiColor('xterm') {
            println "${it}"
          }
		    
	    }
	    sh ' exit 1 ; '
     }
   
	 
}

def promoteFunc(artifactory_instance,from_artifactory_repo,to_artifactory_repo,it){
    
    withCredentials([usernamePassword(credentialsId: 'gip_sv01_artifactory_eu', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
			 
			 sh returnStatus: false, script:"""
			     unset http_proxy
				 unset https_proxy
			     
			     
				 if wget --spider ${artifactory_instance}/${to_artifactory_repo}/${it}/ 2>/dev/null; then
				    echo " #############################################"
                    echo "ISO image  ${it} already exists in ${to_artifactory_repo} release repository"
					echo " #############################################"
					
				else
				    echo " #############################################"
                    echo "ISO image  ${it}   does not exist in ${to_artifactory_repo} and so can be promoted from ${from_artifactory_repo} repository" 
					echo " #############################################"
					echo " #############################################"
					echo " Promoting ISO image   ${it.value} from ${from_artifactory_repo} repo  to ${to_artifactory_repo} repository"
					echo " #############################################"
				   
				     statusCode=`curl -L -i -H "X-JFrog-Art-Api:$PASSWORD" -X POST '${artifactory_instance}/api/copy/${from_artifactory_repo}/${it}?to=/${to_artifactory_repo}/${it}'`
				   echo " exit status = \${statusCode} "
				   if [ "\${statusCode}" -ne "200" ]; then
                       echo \" ISO image promotion failed for  ${it}" >>ISO-promotion-failure.txt
                         
                   else
					   echo " #############################################"
				       echo " ISO image ${it} promoted successfully " >>ISO-promotion-success-summary.txt
					   echo " #############################################"
				   fi
                 fi	
			"""
			 
		 }
}
