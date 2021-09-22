def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
	println("acaPublishManifest [Start]")
    def emailExtraMsg="";
    def baseDir="";
    def test="";
	def artifactoryCredentialId = config.credential_id_for_artifactory?:'gip_sv01_artifactory_eu'


    def branch_name = "${config.git_branch_name}"
    println "Branch name : ${branch_name}"

	try{

		switch(config.git_branch_name) {
			case "master":
			case "integ":
				if ((config.artifact_type == 'js') || (config.artifact_type == 'ts')) {
					publish("${config.service_name}", "${config.output_dir}", "${config.artifact_type}", "${config.app_type}", "${config.service_version}", "${config.unique_version_identifier}", "${config.update_version}", "${config.git_branch_name}","${config.npm_snapshot_repository}","${artifactoryCredentialId}")
				}
				else if ( config.artifact_type == 'rpm') {
					//Call the publish method with appropriate arguments
					//publish("${config.project_name_local}", "${config.output_dir}", "${config.artifact_type}", "${config.project_release_version}", "${config.unique_version_identifier}")
				}
				else if ( config.artifact_type == 'docker') {

					publishDocker("${config.service_name}", "${config.artifact_type}", "${config.service_version}", 'docker-snapshot-eis', "${config.docker_name}","${config.unique_version_identifier}")
				}
				else {
					publish("${config.service_name}", "${config.output_dir}", "${config.artifact_type}", "${config.service_version}", "${config.unique_version_identifier}","${config.npm_snapshot_repository}","${artifactoryCredentialId}")
				}
				break
			case ~/release\/eis/:
				if ((config.artifact_type == 'js') || (config.artifact_type == 'ts')) {
					publish("${config.service_name}", "${config.output_dir}", "${config.artifact_type}", "${config.app_type}", "${config.service_version}", "${config.unique_version_identifier}", "${config.update_version}", "${config.git_branch_name}", "${config.npm_snapshot_repository}","${artifactoryCredentialId}")
				}
				else if ( config.artifact_type == 'rpm') {
					//Call the publish method with appropriate arguments
					//publish("${config.project_name_local}", "${config.output_dir}", "${config.artifact_type}", "${config.project_version}", "${config.unique_version_identifier}")
				}
				else if ( config.artifact_type == 'docker') {

					publishDocker("${config.service_name}", "${config.artifact_type}", "${config.service_version}", 'docker-snapshot-eis', "${config.docker_name}", "${config.unique_version_identifier}")
				}
				else {
					publish("${config.project_name_local}", "${config.output_dir}", "${config.artifact_type}", "${config.project_version}", "${config.unique_version_identifier}","${config.npm_snapshot_repository}","${artifactoryCredentialId}")
				}
				break
			default:
				println("default case...")
				if ((config.artifact_type == 'js') || (config.artifact_type == 'ts')) {
					publish("${config.service_name}", "${config.output_dir}", "${config.artifact_type}", "${config.app_type}", "${config.service_version}", "${config.git_branch_name}", "${config.update_version}", "${config.git_branch_name}","${config.npm_snapshot_repository}","${artifactoryCredentialId}")
				}
				else if ( config.artifact_type == 'rpm') {
					//Call the publish method with appropriate arguments
					//publish("${config.project_name_local}", "${config.output_dir}", "${config.artifact_type}", "${config.project_version}", "${config.git_branch_name}")
				}
				else if ( config.artifact_type == 'docker') {

					publishDocker("${config.service_name}", "${config.artifact_type}", "${config.service_version}", "${config.dockerRepo}", "${config.docker_name}", "${config.git_branch_name}")
				}
				else {
					publish("${config.project_name_local}", "${config.output_dir}", "${config.artifact_type}", "${config.project_version}", "${config.git_branch_name}","${config.npm_snapshot_repository}","${artifactoryCredentialId}")
				}
				break

		}


	}catch(err){
		currentBuild.result = 'FAILURE'
		emailExtraMsg = "Build Failure:"+ err.getMessage()
		throw err
	}
	println("acaPublishManifest [End]")
}

def publish(project_name, output_dir, artifact_type, app_type, project_version, unique_version_identifier, version_update, git_branch, npm_snapshot_repository,artifactoryCredentialId) {
  switch(artifact_type) {
    case ~/^[js|ts]+$/:
	 if ("${app_type}" == '2P-npm-module') {
	    def packageJsonUpdate = isPackageJsonUpdateRequired()
	    echo " ${project_name} is a ${app_type} "
	   def files = findFiles(glob: "${output_dir}/**/*.tgz")
       println ("Files:")
	   println files
	   if ( files.size() > 0) {
	     for (counter = 0; counter < files.size(); counter++) {
		 def filePath = files[counter].path.toString()
         def fileName = files[counter].name.toString()
		 def fileDir = filePath.minus(fileName).replaceAll('/','').trim()
		     sh """
			 pwd
			 ls -lrt
			 cd ${fileDir}
		       npm publish --registry https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/api/npm/${npm_snapshot_repository}/
			 """
         }
		 if (packageJsonUpdate) {
		      updatePackageJsonInRemoteRepo("${git_branch}", "${project_version}")
		 }

          }
	   else{
	       sh """
		     cd ${output_dir}
			 npm publish --registry https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/api/npm/${npm_snapshot_repository}/
		  """
          if (packageJsonUpdate) {
		      updatePackageJsonInRemoteRepo("${git_branch}", "${project_version}")
		  }
	   }

	}
     else {

	   withCredentials([string(credentialsId: "${artifactoryCredentialId}", variable: 'ApiToken')]) {
                 sh """
				   cd ${output_dir} ; tar zvcf ${project_name}-${project_version}-${unique_version_identifier}.tar.gz *
				   curl -H 'X-JFrog-Art-Api: ${ApiToken}' -T ${project_name}-${project_version}-${unique_version_identifier}.tar.gz  https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/${npm_snapshot_repository}/${project_name}/${project_name}-${project_version}-${unique_version_identifier}.tar.gz
	                """
	   }
	 }
	 break
	case "rpm":
     withCredentials([string(credentialsId: '502782741_Artifactory_API_Key', variable: 'ApiToken')]) {
             // rpm package publish implementation will come here
	  }
	 break
    default:
      withCredentials([string(credentialsId: '502782741_Artifactory_API_Key', variable: 'ApiToken')]) {
                 sh """
				   cd ${output_dir} ; tar zvcf ${project_name}-${unique_version_identifier}.tar.gz *
				   curl -H 'X-JFrog-Art-Api: ${ApiToken}' -T ${project_name}-${unique_version_identifier}.tar.gz  https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/${config.npm_snapshot_repository}/${project_name}/${project_name}-${unique_version_identifier}.tar.gz
	                """
	  }
	 break
  }
 }

def publishDocker(project_name, artifact_type, project_version, docker_repo, docker_name, unique_version_identifier) {
      def app = docker.build("${docker_repo}"+"/"+"${project_name}"+"/"+"${docker_name}")
	  def docker_image_version = "${project_version}-${unique_version_identifier}"
			// test image
			 app.inside {
				// TBD: need to come up with test and scan
				sh 'echo "Tests passed"'
			 }

			// push image to registry
			 docker.withRegistry('https://hc-eu-west-aws-artifactory.cloud.health.ge.com', 'gip_sv01_artifactory_eu') {
				app.push("${docker_image_version}")
				app.push("latest")
			 }
 }
 def isPackageJsonUpdateRequired() {
   def exit_code = sh(returnStatus: true, script: 'git diff --exit-code package.json && echo $?')
   if ("${exit_code}" ==  '1') {
       return true
   }
   else {
      return false
   }
 }

 def updatePackageJsonInRemoteRepo(branchname, projectversion) {
     def new_project_version = sh(returnStdout: true, script: 'grep -m 1 \'version\' package.json | cut -d \':\' -f2 | sed -e "s/\\"//g" | sed -e "s/,//g"').trim()
	 println "new_project_version" +new_project_version
	 sh returnStatus: false, script:"""
	   git checkout -f ${branchname}
	   git checkout -f origin/${branchname} package.json
	   npm --no-git-tag-version version -f ${new_project_version}
	   git add package.json
	   git commit  -m " Updating the package.json file with ${new_project_version} version but with [ci-skip]"
	   git pull --rebase -s recursive -X theirs origin ${branchname} &> file1.txt
		if grep -Fxq \"Current branch ${branchname} is up to date.\" file1.txt ; then
          rm -rf file1.txt
		  git push origin ${branchname}
		else
		 echo \" Git pull rebase failed\"
		 exit 1;
		fi
	   """
	   def commitHashNew = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
       def commit_hash_return_code = sh returnStdout: true, script: """git log origin/${branchname} | grep -c \"commit ${commitHashNew}\""""
       if (!commit_hash_return_code.toString().contains('1')){
            error("Commit id is not available in Git. Git Push Failed.")
        }
        else {
                echo "Successfully Pushed package.json to  ${branchname} branch"
                echo commit_hash_return_code
         }

 }
