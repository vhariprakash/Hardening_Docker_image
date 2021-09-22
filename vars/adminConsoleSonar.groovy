def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    def sonarBranch = params.sourceBranch
    def sonarProjectKey = config.sonarProjectKey
    def sonarProjectName = config.sonarProjectName
    if(!(params.sourceBranch == 'master')) {
        sonarProjectKey = config.sonarProjectKey + ".${params.sourceBranch}"
        sonarProjectName = config.sonarProjectName + ".${params.sourceBranch}"
    }
    def sonarExclusions = config.sonarExclusions
    def sonarHostUrl = config.sonarHostUrl?:'http://hcit-sonar.health.ge.com:9000'
    def sonarProfile = config.sonarProfile
    def sonarSources = config.sonarSources
    def sonarLanguage = config.sonarLanguage
	def sonarJavaBinaries = config.sonarJavaBinaries?:'build/classes'
	def sonarProjectVersion = config.sonarProjectVersion
    def sonarSourceEncoding = config.sonarSourceEncoding?:'UTF-8'
    def sonarCodeCoveragePlugin = config.sonarCodeCoveragePlugin?:'jacoco'
    //def sonarCoverageToolReportPath = config.sonarCoverageToolReportPath?:"build/jacoco/test.exec"
	def sonarVerbose = config.sonarVerbose?:'true'
    try {
        /*withSonarQubeEnv('SONAR_MKE') {
        sh """gradle --info sonarqube -Dsonar.profile=${sonarProfile} \
        -Dsonar.host.url=${sonarHostUrl} \
        -Dsonar.projectKey=${sonarProjectKey} \
        -Dsonar.projectName=${sonarProjectName} \
        -Dsonar.sources=${sonarSources} \
        -Dsonar.sourceEncoding=${sonarSourceEncoding} \
        -Dsonar.java.coveragePlugin=${sonarCodeCoveragePlugin} \
        -Dsonar.${sonarCodeCoveragePlugin}.reportPath=${sonarCoverageToolReportPath} \
        -Dsonar.exclusions=${sonarExclusions} \
        -Dsonar.branch=${sonarBranch}
        """
        } */
	  
	  def scannerHome = tool 'SonarQube-Scanner-3.2.0.1227';  
	  withSonarQubeEnv('SONAR_MKE') {
		 
		 if(sonarLanguage == 'java') {
		     def sonarCoverageToolReportPath = config.sonarCoverageToolReportPath?:"build/jacoco/test.exec"
          sh """
		   # npm install -g typescript
			#export NODE_PATH=/usr/lib/node_modules/
			gradle --info jacocoTestReport
			${scannerHome}/bin/sonar-scanner \
			-Dsonar.host.url=${sonarHostUrl} \
			-Dsonar.projectKey=${sonarProjectKey} \
            -Dsonar.projectName=${sonarProjectName} \
            -Dsonar.sources=${sonarSources} \
            -Dsonar.profile=\"${sonarProfile}\" \
			-Dsonar.java.binaries=${sonarJavaBinaries} \
		    -Dsonar.exclusions=${sonarExclusions} \
		    -Dsonar.sourceEncoding=${sonarSourceEncoding} \
		    -Dsonar.${sonarLanguage}.coveragePlugin=${sonarCodeCoveragePlugin} \
		    -Dsonar.${sonarCodeCoveragePlugin}.reportPath=${sonarCoverageToolReportPath} \
            -Dsonar.projectVersion=${sonarProjectVersion} \
		    -Dsonar.verbose=${sonarVerbose} \
            -Dsonar.branch=${sonarBranch}
        """
       
        def checkJacocoReportFile = sh(returnStdout: true, script: "find . -name '*.exec'").trim()
		 sh """ echo " ${checkJacocoReportFile} "
		  if [ -z "${checkJacocoReportFile}" ] ; then
		  echo " Code coverage report not present. Failing the build"
		  exit 1;
		  else
		  echo " Code coverage report is available and captured in the sonar dashboard"
		  fi
		"""
		 }
	   }
	
	   
	withSonarQubeEnv('SONAR_MKE') {
	 
		 if(sonarLanguage == 'typescript') {
		  def sonarCoverageToolReportPath = config.sonarCoverageToolReportPath?:"./node_modules/app-root-path/coverage.lcov"
          sh """
		   npm install -g typescript
			export NODE_PATH=/usr/lib/node_modules/
			${scannerHome}/bin/sonar-scanner -X \
			-Dsonar.host.url=${sonarHostUrl} \
			-Dsonar.projectKey=${sonarProjectKey} \
            -Dsonar.projectName=${sonarProjectName} \
            -Dsonar.sources=${sonarSources} \
            -Dsonar.profile=\"${sonarProfile}\" \
			-Dsonar.exclusions=${sonarExclusions} \
		    -Dsonar.sourceEncoding=${sonarSourceEncoding} \
		    -Dsonar.projectVersion=${sonarProjectVersion} \
		    -Dsonar.verbose=${sonarVerbose} \
            -Dsonar.branch=${sonarBranch} 
            
        """
        }
	  } 
	  withSonarQubeEnv('SONAR_MKE') {
	 
		 if(sonarLanguage == 'javascript') {
		  def sonarCoverageToolReportPath = config.sonarCoverageToolReportPath?:"./node_modules/app-root-path/coverage.lcov"
          sh """
		   
			export NODE_PATH=/usr/lib/node_modules/
			${scannerHome}/bin/sonar-scanner -X \
			-Dsonar.host.url=${sonarHostUrl} \
			-Dsonar.projectKey=${sonarProjectKey} \
            -Dsonar.projectName=${sonarProjectName} \
            -Dsonar.sources=${sonarSources} \
            -Dsonar.profile=\"${sonarProfile}\" \
			-Dsonar.exclusions=${sonarExclusions} \
		    -Dsonar.sourceEncoding=${sonarSourceEncoding} \
		    -Dsonar.projectVersion=${sonarProjectVersion} \
		    -Dsonar.verbose=${sonarVerbose} \
            -Dsonar.branch=${sonarBranch} 
            
        """
        }
	  }
        /*
		 def checkJacocoReportFile = sh(returnStdout: true, script: "find . -name '*.exec'").trim()
		 sh """ echo " ${checkJacocoReportFile} "
		  if [ -z "${checkJacocoReportFile}" ] ; then
		  echo " Code coverage report not present. Failing the build"
		  exit 1;
		  else
		  echo " Code coverage report is available and captured in the sonar dashboard"
		  fi
		"""
		*/
    }
    catch(err) {
        echo 'Something wrong with SONAR.' + err
        throw err
    }
}
