def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def emailExtraMsg="";
    def baseDir="";
    def test="";






        try{

             switch(config.build_type) {
                       case "gradlenpm":
                         println config.build_type
						 println "service_name in acaBuild"+config.service_name
						 acaBuild("${config.service_name}", "${config.build_type_home}", "${config.service_version}", "${config.app_type}", "${config.service_update}", "${config.customBuildCommand}", "${config.git_branch_name}","${config.npm_snapshot_repository}")

						break
                       case "gradle":
                          // code to build pure gradle based project comes here
                          acaBuild("${config.service_name}", "${config.build_type_home}", "${config.service_version}", "${config.app_type}", "${config.service_update}", "${config.customBuildCommand}", "${config.git_branch_name}")
                         break
					   case "mvn":
                          // code to build maven based projects comes here
                         break
					   case "npm":
					      println config.build_type
                          // code to build npm based projects comes here
						  npmBuild("${config.service_name}", "${config.build_type_home}", "${config.service_version}", "${config.app_type}", "${config.service_update}", "${config.require_build}", "${config.customBuildCommand}", "${config.git_branch_name}","${config.npm_snapshot_repository}")
                         break
                       default:
                         // code to build custom projects comes here
                         break
              }

	}
       catch(err) {
		  echo 'Failed to build the ACA project '+ err
		 throw err
	   }



}

def npmBuild(service_name, build_type_home, service_version, app_type, service_update, build_required, customBuildCommand, branch_name,npm_snapshot_repository) {
    if(build_required == 'yes') {

	 if (!((branch_name == 'master') || (branch_name =~ /release\/eis/))) {
	    service_update = packageJsonVersionIncrementerForFeatureBranches("${service_version}","${branch_name}")
	 }
      else {
	      service_version = updatePackageVersionIfRequired("${service_version}")
	  }
     sh returnStatus: true, script:"""
        if wget --spider https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/${npm_snapshot_repository}/${service_name}/-/${service_name}-${service_version}.tgz &> file.txt; then
	       echo " ${service_name}-${service_version}.tgz already exists in the npm repository and so a new version will be published"
		   echo "${service_update}"
	       npm --no-git-tag-version version -f ${service_update}
		else
           echo " ${service_name}-${service_version}.tgz does not  exist in the npm repository and so npm version change is not required"
        fi
     """
	  // Implementation to build npm based project will come here
	}
    else {
	   if (!((branch_name == 'master') || (branch_name =~ /release\/eis/))) {
	      service_update = packageJsonVersionIncrementerForFeatureBranches("${service_version}","${branch_name}")
	   }
	   else {
	      service_version = updatePackageVersionIfRequired("${service_version}")
	   }
	   sh returnStatus: true, script:"""
        if wget --spider https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/${npm_snapshot_repository}/${service_name}/-/${service_name}-${service_version}.tgz &> file.txt; then
	       echo " ${service_name}-${service_version}.tgz already exists in the npm repository and so a new version will be published"
		   echo "${service_update}"
	       npm --no-git-tag-version version -f ${service_update}
		else
           echo " ${service_name}-${service_version}.tgz does not  exist in the npm repository and so npm version change is not required"
        fi
     """
	}

}
def acaBuild(service_name, build_type_home, service_version, app_type, service_update, customBuildCommand, branch_name, npm_snapshot_repository) {
    if(app_type == '2P-npm-module') {
	 if (!((branch_name == 'master') || (branch_name =~ /release\/eis/))) {
	      service_update = packageJsonVersionIncrementerForFeatureBranches("${service_version}","${branch_name}")
	   }
	  else {
	      //service_version = updatePackageVersionIfRequired("${service_version}")
		  service_update = updatePackageVersionIfRequired("${service_version}")
	  }
     sh returnStatus: true, script:"""
        if wget --spider https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/${npm_snapshot_repository}/${service_name}/-/${service_name}-${service_version}.tgz &> file.txt; then
	       echo " ${service_name}-${service_version}.tgz already exists in the npm repository and so a new version will be published"
		   echo "${service_update}"
		   npm --no-git-tag-version version -f ${service_update}
        else
           echo " ${service_name}-${service_version}.tgz does not  exist in the npm repository and so npm version change is not required"
        fi
     """
	 def files = findFiles(glob: "**/package.json")

	 if (files.size() > 1) {
	    updateMultiplePackageJsonList()
	 }

	 sh """
	    ${build_type_home} --no-daemon clean build ${customBuildCommand}
	 """
	}
    else {
	   sh """
	    ${build_type_home} --no-daemon clean build ${customBuildCommand}
      """
	}

}

def packageJsonVersionIncrementerForFeatureBranches(service_version,branch_name) {

					     if (service_version.contains("${branch_name}")) {
                             echo "${service_version}"
						     service_version = service_version.replace("${branch_name}-", '').trim()
						     echo "${service_version}"
							 version_incrementer = (service_version.split('-')[-1]).trim().toInteger()
							 echo "${version_incrementer}"
							 service_version = (service_version.split('-')[-2]).trim()

							 echo "${service_version}"
							 version_incrementer = (version_incrementer + 1).toString()
							 version_update = service_version + '-' + branch_name + '-' + version_incrementer
							 echo "${version_update}"
							 return version_update
						 }
						 else {
						    version_update = service_version + '-' + branch_name + '-' + '0'
							return version_update
						 }


}

def updatePackageVersionIfRequired(service_version) {
      def version_update
      if (service_version.contains('-')) {
         version_update = (service_version.split('-')[0]).trim()
		 sh """
		    npm --no-git-tag-version version -f ${version_update}
		 """
		 return version_update
      }
     else{
       version_update = service_version
	   return version_update
	 }

}
def updateMultiplePackageJsonList() {
    def package_version
	package_version = sh(returnStdout: true, script: 'grep -m 1 \'version\' package.json | cut -d \':\' -f2 | sed -e "s/\\"//g" | sed -e "s/,//g"').trim()
	echo "${package_version}"
    def files = findFiles(glob: "**/package.json")
    echo "${files.size()}"
    //def listOfFiles = []
    for (i = 0; i < files.size(); i++) {
        def filePath = files[i].path.toString()
        def fileName = files[i].name.toString()
        def fileDir
        echo "${fileDir}"
        echo " ${filePath}"
        echo " ${fileName}"
		//listOfFiles.add("${filePath}")
        //echo " ${directory}"
      if ("${filePath}" == "${fileName}") {
          echo " ${files[i].name} exists in the current directory. No action required"
       }
      else {
          fileDir = filePath.minus(fileName).replaceAll('/','').trim()
          sh """
            cd ${fileDir}
            echo "${files[i].name}"
            npm --no-git-tag-version version -f ${package_version} --allow-same-version
         """
       }
      }

}



