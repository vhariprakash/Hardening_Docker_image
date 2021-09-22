def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def sonarBranch = (params.sourceBranch).replaceAll('/','-')
    def sonarProjectKey = config.sonarProjectKey
    def sonarProjectName = config.sonarProjectName
    def sonarExclusions = config.sonarExclusions
    def sonarHostUrl = config.sonarHostUrl?:'https://sonar.cloud.health.ge.com'
    def sonarSources = config.sonarSources
    def sonarLanguage = config.sonarLanguage
	def sonarJavaBinaries = config.sonarJavaBinaries?:'build/classes'
	def sonarProjectVersion = config.sonarProjectVersion
    def sonarSourceEncoding = config.sonarSourceEncoding?:'UTF-8'
    def sonarCodeCoveragePlugin = config.sonarCodeCoveragePlugin?:'jacoco'
	def sonarVerbose = config.sonarVerbose?:'true'
    try {


	  def scannerHome = tool 'SonarQube-Scanner-3.2.0.1227';
	  withSonarQubeEnv('SONAR_CLOUD_PROD') {

		 if(sonarLanguage == 'java') {
		     def sonarCoverageToolReportPath = config.sonarCoverageToolReportPath ?: "build/reports/jacoco/test/jacocoTestReport.xml"
          sh """

			gradle --info  jacocoTestReport
			${scannerHome}/bin/sonar-scanner \
			-Dsonar.projectKey=${sonarProjectKey} \
            -Dsonar.projectName=${sonarProjectName} \
            -Dsonar.sources=${sonarSources} \
			-Dsonar.java.binaries=${sonarJavaBinaries} \
		    -Dsonar.exclusions=${sonarExclusions} \
		    -Dsonar.sourceEncoding=${sonarSourceEncoding} \
		    -Dsonar.coverage.jacoco.xmlReportPaths=${sonarCoverageToolReportPath} \
            -Dsonar.projectVersion=${sonarProjectVersion} \
		    -Dsonar.verbose=${sonarVerbose} \
            -Dsonar.branch.name=${sonarBranch}
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


	withSonarQubeEnv('SONAR_CLOUD_PROD') {

		 if(sonarLanguage == 'typescript') {
		  def sonarCoverageToolReportPath = config.sonarCoverageToolReportPath?:"coverage/lcov.info"
          sh """
		   npm install -g typescript
			export NODE_PATH=/usr/lib/node_modules/
			${scannerHome}/bin/sonar-scanner -X \
			-Dsonar.projectKey=${sonarProjectKey} \
            -Dsonar.projectName=${sonarProjectName} \
            -Dsonar.sources=${sonarSources} \
			-Dsonar.exclusions=${sonarExclusions} \
		    -Dsonar.sourceEncoding=${sonarSourceEncoding} \
		    -Dsonar.projectVersion=${sonarProjectVersion} \
		    -Dsonar.verbose=${sonarVerbose} \
			-Dsonar.typescript.lcov.reportPaths=${sonarCoverageToolReportPath} \
			-Dsonar.branch.name=${sonarBranch}

        """
        }
	  }
	  withSonarQubeEnv('SONAR_CLOUD_PROD') {

		 if(sonarLanguage == 'javascript') {
		  def sonarCoverageToolReportPath = config.sonarCoverageToolReportPath?:"coverage/lcov.info"
          sh """

			export NODE_PATH=/usr/lib/node_modules/
			${scannerHome}/bin/sonar-scanner -X \
			-Dsonar.projectKey=${sonarProjectKey} \
            -Dsonar.projectName=${sonarProjectName} \
            -Dsonar.sources=${sonarSources} \
			-Dsonar.exclusions=${sonarExclusions} \
		    -Dsonar.sourceEncoding=${sonarSourceEncoding} \
		    -Dsonar.projectVersion=${sonarProjectVersion} \
		    -Dsonar.verbose=${sonarVerbose} \
            -Dsonar.javascript.lcov.reportPaths=${sonarCoverageToolReportPath} \
		    -Dsonar.branch.name=${sonarBranch}

        """
        }
	  }

    }
    catch(err) {
        echo 'Something wrong with SONAR.' + err
        throw err
    }
}
