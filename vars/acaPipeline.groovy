import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def emailExtraMsg="";
    def baseDir="";
    def test="";
    def debugMap
    //Devops settings.yaml location, branch and repository name
    def settings_location = "vars/settings.yaml"
    def settings_repo_branch = "cd-pipeline-coreload"
    def settings_repo = "common-lib-devops"
    def credentials_id = "ssh-Jenkins-s02"
    def modality = "${config.Modality}"

	//def buildnode
    def WORKSPACE = "/dockerspace/coreload-5.x/workspace/${env.JOB_NAME}"
    def hostip_bdd = "${config.hostip_dev_env}"
    def enableIsoDeployment = "${config.enableIsoDeployment}"
    def buildnode=config.buildNode?:'docker06'
    def releaseVersion
    def gitlabBranch = "${env.gitlabBranch}"
    def group_name = "${env.Group_Name}"
    def service_Name = "${config.serviceName}"
    def projectName = "${config.project}"
    println "projectName "+projectName
    def HELM_CHART_LOCATION="${WORKSPACE}/${config.HelmChartLocation}"
    def HELM_TGZ_FILE_NAME="${config.HelmTgzFileName}"
    def SERVICE_VERSION="${config.ServiceVersion}"
    def ISO_NAME="${config.IsoName}"
    def APP_MANIFEST_FILE="${WORKSPACE}/${config.AppManifestFile}"
    def DOCKER_TAR_NAME="${config.DockerTarName}"
    def ISO_FOLDER_NAME="${config.IsoFolderName}"
    def ISO_HELM_CHART_LOCATION="${ISO_FOLDER_NAME}/helm"
    def ISO_DOCKER_IMAGES_LOCATION="${ISO_FOLDER_NAME}/docker_images"
    def ISO_GENERIC="${ISO_FOLDER_NAME}/data/generic/"
    def ISO_ACA="${ISO_FOLDER_NAME}/data/aca"
    def ISO_APP_MANIFEST="${ISO_FOLDER_NAME}/manifest"
    def isotargetRepo = config.isotargetRepo?: 'generic-coreload-snapshot'
    def deploy_git_branch_dsl = 'cd-pipeline-coreload'
    def deploy_git_repo_dsl = 'git@gitlab-gxp.cloud.health.ge.com:Edison-Imaging-Service/devops-dsl.git'
    def branch_name = "${params.sourceBranch}"
    def generalStageUtils = new org.utils.generalStageUtils()
    //---------------Common config
    /*def commitHashData = readYaml file: ('commitHash.yaml')
	commit_hash_ci_skip = commitHashData['commit_hash_ci_skip']*/


    node("${buildnode}") {
	    env.JAAS_LOGGER_LEVEL= "FINE"
        def repo_name
		def gitSwarmUrl_ssh
        echo " Branch is ${params.sourceBranch}"
        boolean isMasterOrRelease = (params.sourceBranch == 'master' || params.sourceBranch == 'integ') ?: false
        def hcddSettings= new hcdd.Settings()
        hcddSettings.org= 'GE Healthcare'
        hcddSettings.team= 'Coreload-EIS'
        hcddSettings.program= 'EIS Platform Services'
        hcddSettings.product= 'EIS'
        hcddSettings.branch= "${params.sourceBranch}"
        hcddSettings.release= '1.0.0'
        hcddSettings.component= "${env.JOB_NAME}"
        hcddSettings.pipelinePhase= 'DEV'
        def docker_repo_name = (("${params.sourceBranch}" == 'master') || ("${params.sourceBranch}" ==~ 'release/eis.*'))? 'docker-coreload-snapshot': 'docker-coreload-dev'
        try{
		    String artifactType
			String project
			def artifactName
			def project_name_local
			def project_version
			def build_engine
			def build_engine_home
			def build_required
			def project_update_version
			def isSaasComponent=config.isSaasOnly?:'yes'
			def gitBranchName = "${params.sourceBranch}"
            println("gitBranchName : $gitBranchName")
            // scm object to make git calls
            def scmObj = new org.utils.scm()
            // Shell Object to call linux commands / scripts
            def shellObj = new org.utils.shell()
            def generalDisplayUtils = new org.utils.generalDisplayUtils()
            def enable_stages = config.enable_stages
            def artifactory_loc=config.artifactoryLocation?:"${env.artifactory_location}"
            dir ("common-vars") {
                git branch: "${settings_repo_branch}", credentialsId: "${credentials_id}", changelog: false, poll: false, url: "git@gitlab-gxp.cloud.health.ge.com:imaging-devops/${settings_repo}.git"
                settings_yaml = readYaml file: ("${settings_location}")
            }
			def artifactory_site=config.artifactoryUrl?:settings_yaml["${modality}"]['default']['docker_artifactory_url']
			def artifactory_docker_repo_name=config.artifactoryDockerRepo?:"${env.eis_snapshot_docker_repo}"
			def artifactory_generic_repo_name=config.artifactoryGenericRepo?:"${env.eis_generic_snapshot_repo}"
            def manifest_file_loc
            String commit_hash_ci_skip= ''

            def isoGroupName = "${config.iso_group_name}"
            def manifest_file_absolute_loc = pwd() + '/' + "${config.manifest_folder}/${config.manifest_file_name}"
            def sleepTime = 60
            def eesCheckoutBranch = "${params.sourceBranch}"
            //def projectName = config.project
            if (config.delay) {
                sleepTime = "${config.delay_for_resource_deletion}".toInteger()
            }
            def imageReferenceMap = config.dockerImageReferences
            currentBuild.description = "<span style=\"background-color:green;color:#fff;padding:5px;\">${params.sourceBranch}</span>"
            def blockBuilds = "${env.block_builds}"
            def blockedBranches = "${env.blocked_branches}"
            //gitlabCommitStatus(builds: ["code-checkout", "Build and Unit test", "Sonar Analysis", "Publish Artifacts", "Scan Artifacts", "Deploy", "BDD","Update Manifest"]) {
            stage('Code Checkout') {
                if(enable_stages.contains('code_checkout')){
                    //jaas_sensor_step{
                    //name= "Code Checkout"
                    //settings= hcddSettings
                    // jaas_step={
                    code_checkout_triggered_repo()
                    //}
                    def code_checkout_url = shellObj.shell_ret_output("git config --local remote.origin.url")
                    println("code_checkout_url: "+code_checkout_url)
                    repo_name = code_checkout_url.split('/')[-1].split('\\.')[0]

                    gitSwarmUrl_ssh = shellObj.shell_ret_output("git config remote.origin.url")
                    println(gitSwarmUrl_ssh)

                }
                else{
                    Utils.markStageSkippedForConditional('Code Checkout')
                }
            }

                // checking for the build engine and setting some basic parameters
            if ((fileExists('build.gradle')) && (fileExists('package.json')))  {
                build_engine = 'gradlenpm'
                project_name_local = sh(returnStdout: true, script: 'grep -m 1 \'name\' package.json | cut -d \':\' -f2 | sed -e "s/\\"//g" | sed -e "s/,//g"').trim()
                project_version = sh(returnStdout: true, script: 'grep -m 1 \'version\' package.json | cut -d \':\' -f2 | sed -e "s/\\"//g" | sed -e "s/,//g"').trim()
                build_engine_home = config.gradleCmd ?: '/dockerspace/gradle-3.5/bin/gradle'
                build_required = 'yes'
                project_update_version = config.updateVersion
                //echo "${project_update_version}"
                env.CHROMIUM_BIN='/usr/lib64/chromium-browser/headless_shell'
                env.CHROME_BIN='/usr/lib64/chromium-browser/headless_shell'
            }
		    else if ((fileExists('build.gradle')) && !(fileExists('package.json')))  {
                build_engine = 'gradle'
                build_required = 'yes'
                build_engine_home = config.gradleCmd ?:  '/dockerspace/gradle-3.5/bin/gradle'
                project_name_local = sh(returnStdout: true, script: 'grep -m 1 \'name\' package.json | cut -d \':\' -f2 | sed -e "s/\\"//g" | sed -e "s/,//g"').trim()
                project_version = sh(returnStdout: true, script: 'grep -m 1 "version[[:blank:]]\\+=" build.gradle | awk \'{print $3}\'  | sed -e "s/[^0-9\\.]//g"').trim()
            }
		    else if (!(fileExists('build.gradle')) && (fileExists('package.json')))  {
                build_engine = 'npm'
                project_name_local = sh(returnStdout: true, script: 'grep -m 1 \'name\' package.json | cut -d \':\' -f2 | sed -e "s/\\"//g" | sed -e "s/,//g"').trim()
                project_version = sh(returnStdout: true, script: 'grep -m 1 \'version\' package.json | cut -d \':\' -f2 | sed -e "s/\\"//g" | sed -e "s/,//g"').trim()
                echo "${project_version}"
                build_engine_home = 'npm'
                build_required = config.isBuildRequired
                project_update_version = config.updateVersion
            }
			else if (fileExists('pom.xml'))  {
                build_engine = 'mvn'
                build_required = 'yes'
                project_name_local = sh(returnStdout: true, script: 'grep -m 1 \'name\' package.json | cut -d \':\' -f2 | sed -e "s/\\"//g" | sed -e "s/,//g"').trim()
                project_version = sh(returnStdout: true, script: 'grep -m 1 "version[[:blank:]]\\+=" build.gradle | awk \'{print $3}\'  | sed -e "s/[^0-9\\.]//g"').trim()
            }
			else {
                build_engine = config.buildEngine
                project_name_local = config.projectName
                project_version = project_version
			}

			def commitHash = shellObj.shell_ret_output("git rev-parse --short HEAD")
            def bddFeatureFilesLocation = config.bddFeatureFilesLocation?:'component-test/src/test/resources/feature/'
            def list_of_changed_feature_files = []

                //START: Code to check if there is a change in feature file
            if(("${config.enableEtymo}"=='true') && (("${params.sourceBranch}" == 'master') || ("${params.sourceBranch}" ==~ 'release/eis.*'))) {
                list_of_changed_feature_files = scmObj.check_commit_mesg_and_list_files("${bddFeatureFilesLocation}","*.feature",'1',"ci-skip", 'false')
                println(list_of_changed_feature_files)

                String feature_update_flag
                println feature_update_flag
                if ( (list_of_changed_feature_files == null) || (list_of_changed_feature_files.size() == 0 )) {
                    feature_update_flag = 'false'
                }
                else{
                    feature_update_flag = 'true'
                }
                echo "${feature_update_flag}"
                if(feature_update_flag=='true'){
                    echo 'Etymo steps should get executed now'
                }
                else {
                    echo "Etymo steps won't be executed since there is no change in feature file"
                }

                if(enable_stages.contains('alm_integration')){
                    stage('ALM Integration') {
                        if(feature_update_flag=='true'){
                            def testHeadEtymo = "${config.testHead}".toString().trim()
                            def requestHeadEtymo = "${config.requestHead}".toString().trim()
                            def extra_parameters = "${config.etymoParameters}"
                            def commit_message = []
                            commit_message = list_of_changed_feature_files

                            etymo {
                                feature_file_loc = "${bddFeatureFilesLocation}".toString().trim()
                                test_head = "${testHeadEtymo}".toString()
                                request_head = "${requestHeadEtymo}".toString()
                                etymo_extra_parameters = "${extra_parameters}"
                                commitMessage = commit_message

                            }
                        }
                    }
                }
            }

            stage("Build and Unit test") {
                if(enable_stages.contains('build_and_unit_test')){
                    acaBuild {
                        build_type = "${build_engine}"
                        customBuildCommand = config.customBuildCommand
                        build_type_home = "${build_engine_home}"
                        app_type = config.appType
                        service_name = "${project_name_local}"
                        service_version = "${project_version}"
                        service_update = "${project_update_version}"
                        require_build = "${build_required}"
                        git_branch_name = "${gitBranchName}"
                        npm_snapshot_repository = "${config.npmSnapshotRepository}"
                    }
                }
                else{
                    Utils.markStageSkippedForConditional('Build and Unit test')
                }
            }

            stage("Sonar Analysis") {
                if(enable_stages.contains('sonar_analysis')) {
                    jaas_sensor_step{
                        name= "Test"
                        settings= hcddSettings
                        jaas_step={
                            acaSonarAnalysis {
                                sonarProjectKey = config.sonarProjectKey
                                sonarProjectName = config.sonarProjectName
                                sonarExclusions = config.sonarExclusions?:''
                                sonarSources = config.sonarSources
                                sonarLanguage = config.sonarLanguage
                                sonarProjectVersion = config.sonarProjectVersion
                                customBuildCommand = config.customBuildCommand
                            }
	                    }
                    }
                    jaas_sonar {
                        reportFile = '.scannerwork/report-task.txt'
                        sonarUserCredential = 'sonar_prod_token'
                        failBuild = isMasterOrRelease
                        settings = hcddSettings
                        sonarTimeout = 5
                    }
				} else {
                    Utils.markStageSkippedForConditional('Sonar Analysis')
			    }
            }

            stage("Publish Artifacts") {
                if(enable_stages.contains('publish_jar')){
                    def output_artifacts = []
                    output_artifacts = config.outputArtifacts
                    for( item in output_artifacts){
                        artifactType = item
                        project = project_name_local

                        switch(item) {
                        case ~/^[js|ts]+$/:
                            println item
                            //println "service_name in acaPipeline, Publish Artifacts stage "+ service_name
                            println "project_name_local in acaPipeline, Publish Artifacts stage "+ project_name_local

                            acaPublishArtifacts {
                                output_dir = "${config.outputDir}"
                                service_name = "${project_name_local}"
                                service_version = "${project_version}"
                                service_update = "${project_update_version}"
                                git_branch_name = "${gitBranchName}"
                                unique_version_identifier = "${commitHash}"
                                artifact_type = "${artifactType}"
                                app_type = config.appType
                                npm_snapshot_repository = "${config.npmSnapshotRepository}"
                                credential_id_for_artifactory = "${config.credentialIdForNpmArtifactory}"
                                }
                            break
                        case "docker":
                            acaPublishArtifacts {
                                service_name = "${project_name_local}"
                                docker_name = config.dockerName
                                service_version = "${project_version}"
                                unique_version_identifier = "${commitHash}"
                                git_branch_name = "${gitBranchName}"
                                artifact_type = "${artifactType}"
                                dockerRepo = "${config.dockerRepo}"
                            }
                            break
                        case "rpm":
                            println item
                            println " Publishing artifacts of type rpm"
                            break
                        default:
                            println item
                            acaPublishArtifacts {
                                output_dir = config.outputDir
                                git_branch_name = "${gitBranchName}"
                                unique_version_identifier = "${commitHash}"
                                artifact_type = "${artifactType}"
                            }
                            break
                        }
				    }
                }
                else{
                    Utils.markStageSkippedForConditional('Publish Artifacts')
                }
			}

            stage("Scan Artifacts") {
                if(enable_stages.contains('docker_scan')){
                    def output_artifacts = []
                    println output_artifacts + "before output_artifacts"
                    output_artifacts = config.outputArtifacts
                    println output_artifacts + "after output_artifacts"
                    for( item in output_artifacts){
                        switch(item) {
                        case ~/^[js|ts]+$/:
                            //println item
                            println "Implementation to scan output artifacts of type JS should come here"
                            // Implementation to scan output artifacts of type JS should come here

                            break
                        case "docker":
                            println item
                            if ((params.sourceBranch == 'master') || ("${params.sourceBranch}" ==~ 'release/eis.*')) {
                                sh """
                                    echo "${artifactory_loc}/${artifactory_docker_repo_name}/${project_name_local}/${config.dockerName}:${project_version}-${commitHash} `pwd`/Dockerfile" >> anchore_images
                                """
                                anchore bailOnFail: false, engineRetries: '600', name: 'anchore_images'
                            }
                            else {
                                sh """
                                    echo "${artifactory_loc}/docker-coreload-dev/${project_name_local}/${config.dockerName}:${project_version}-${params.sourceBranch} `pwd`/Dockerfile" >> anchore_images
                                """
                                anchore bailOnFail: false, engineRetries: '600', name: 'anchore_images'
                            }
                            break
                        case "rpm":
                            println item
                            // Implementation to scan output artifacts of type RPM should come here
                            break
                        default:
                            println item
                            // Implementation to scan output artifacts of types other than rpm, docker, js should come here
                            break
                        }
                    }
                }
                else{
                    Utils.markStageSkippedForConditional('Scan Artifacts')
                }
			}

               // **---------------------------UPDATE ADDITIONAL VALUES AND MANIFEST FILES IN CI STAGE [BEGINS] * /
            stage ('Update Additional Values'){
                if(enable_stages.contains('update_additional_values')){
                    debugMap = ["branch_name": branch_name]
                    generalDisplayUtils.debugLines(debugMap)
                    def docker_image_tag
                    def helmAppRelease
                    if ((params.sourceBranch != 'release/eis.*') && (branch_name != 'integ')) {
                        docker_image_tag = "${project_version}-${params.sourceBranch}"
                        helmAppRelease   = "${params.sourceBranch}"
                    }
                    else {
                        docker_image_tag = "${project_version}-${commitHash}"
                        helmAppRelease   = commitHash
                    }
                    def additional_file_dir = config.source_deployment_folder
                    def additional_file_absolute_loc = pwd() + '/' + additional_file_dir + '/additional_values.yaml'
                    def docker_image = artifactory_loc +'/' + artifactory_docker_repo_name +'/' + config.project + '/' + "${config.dockerName}"
                    println docker_image
                    println docker_image_tag
                    debugMap = ["additional_file_dir": additional_file_dir, "additional_file_absolute_loc": additional_file_absolute_loc, "docker_image": docker_image,
                                "docker_image_tag": docker_image_tag, "isSaasComponent": isSaasComponent, "commitHash": commitHash,
                                "config.manifest_folder": "${config.manifest_folder}", "imageReferenceMap": imageReferenceMap,
                                "config.source_deployment_folder": "${config.source_deployment_folder}", "branch_name": branch_name,
                                "config.dockerName" : config.dockerName, "config.project" : config.project ]
                    generalDisplayUtils.debugLines(debugMap)
                    sectionDisplay('Update additional_values.yaml')
                    saasCreateAdditionalValues{
                        saasComponent = isSaasComponent
                        helm_app_release = helmAppRelease
                        helm_app_version = project_version
                        manifestFolderLocation = "${config.manifest_folder}"
                        image_reference_map = imageReferenceMap
                        chart_location = "${config.source_deployment_folder}"
                        gitBranchForPush = branch_name
                        docker_image_name = config.dockerName
                        name_of_project = config.project
                        enablemultiService = config.enablemultiService
                        artifactory_repo = config.dockerArtifactoryRepoName
                        artifactory_location = config.docker_artifactory_url
                        helm_chart_location = "${config.source_deployment_folder}"
                        gitSwarmUrl = gitSwarmUrl_ssh
                        credentialsId = credentials_id
                    }
                    println "params.sourceBranch is ${params.sourceBranch}"
                    def branch_parameter = config.branchForAdditionalUpdate?:'master'
                    def commitHashData = readYaml file: ('commitHash.yaml')
                    commit_hash_ci_skip = commitHashData['commit_hash_ci_skip']
                }
                else{
                    Utils.markStageSkippedForConditional("${env.STAGE_NAME}")
                }
            }


            if ((enable_stages.contains('create_app_bundle')) && !(gitBranchName.contains('devops'))){
                stage("Publish Helm Chart"){
                    packageAndPublishHelmChart{
                        helmChartLocation = config.source_deployment_folder
                        nname_of_project = projectName
                        gitUrl = gitSwarmUrl_ssh
                        testEnablerChart = testEnablerChartLocation
                        customConfigLocation = configJsonArtLocation
                        modalityName = modality
                        artifact_url = config.artifactory_url
                        artifactory_credID = config.credID
                    }
                }
                def helmChartFolder = config.source_deployment_folder
                println("helmchart : ${helmChartFolder}/Chart.yaml")
                def chartYamlContent = readYaml file:"${helmChartFolder}/Chart.yaml"
                println (chartYamlContent)
                def helm_package_name = chartYamlContent["name"] + "-" + chartYamlContent["version"] + ".tgz"
                def helm_package_artifactory_url =  "https://${artifactory_url}/artifactory/helm-ees-all/${chartYamlContent["name"]}/${helm_package_name}"

                stage('App Bundle Packaging'){
                    appBundlePackaging {
                        registrationYamlPath       = config.registration_yaml_path
                        appBundleManifestPath      = config.app_bundle_manifest_path
                        chart_name                 = chartYamlContent["name"]
                        chart_version              = chartYamlContent["version"]
                        image_reference_list       = config.imageReferenceList
                        application_chart_location = helmChartFolder
                        app_bundle_chart_folder    = config.appBundleChartFolder
                        appBundleArtifactoryPath   = projectName
                        git_branch_name            = gitBranchName
                        branch_type                = branch_type
                        helm_repo                  = config.helm_repo
                        artifact_url               = config.artifactory_url
                        gitlab_cred_id             = config.gitlab_cred_id ?: 'root-user-docker06'
                        deployRepo                 = config.deployRepo
                        deployRepoBranch          = config.deployRepoBranch
                        yq_arch                    = config.yq_arch
                        artifactory_credID         = config.credID
                    }
                }
            }

            if( config.projectName && ((params.sourceBranch == 'master') || ("${params.sourceBranch}" ==~ 'release/eis.*'))) {
                if(enable_stages.contains('update_references')){
                    stage('Update Manifest') {
                        def deploy_branch_name = "${params.sourceBranch}"
                        def output_artifacts = []
                        output_artifacts = config.outputArtifacts
                        for( item in output_artifacts){
                            artifactType = item
                            project = project_name_local
                            echo "${artifactType}"
                            echo "${project}"
                            switch(item) {
                            case ~/^[js|ts]+$/:
                                println item
                                //artifact_name = "${project_name_local}/${project_name_local}-${project_version}-${commitHash}.tar"
                                acaUpdateManifest {
                                    git_deploy_branch_name = deploy_branch_name
                                    artifact_name = "${project_name_local}/${project_name_local}-${project_version}-${branch_name}.tar"
                                    artifact_path = "https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/${isotargetRepo}"
                                    app_type = config.appType
                                    deploy_project_name = "${project}"
                                    deploymentFolderLocation=config.deployment_folder_location
                                    artifact_type = "${artifactType}"
                                    deployment_type = config.deploymentType
                                }
                                break
                            case "docker":
                                acaUpdateManifest {
                                    git_deploy_branch_name = deploy_branch_name
                                    artifact_name = "${project_name_local}/${project_name_local}:${project_version}-${commitHash}"
                                    deploy_tag = "${project_version}-${commitHash}"
                                    artifact_path = 'hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/docker-snapshot-eis'
                                    app_type = config.appType
                                    deploy_project_name = "${project}"
                                    deploymentFolderLocation=config.deployment_folder_location
                                    artifact_type = "${artifactType}"
                                    deployment_type = config.deploymentType

                                }
                                break
                            case "rpm":
                                println item
                                println " Publishing artifacts of type rpm"
                                break
                            default:
                                println item
                                acaUpdateManifest {
                                    git_deploy_branch_name = deploy_branch_name
                                    artifact_name = "${project_name_local}/${project_name_local}-${project_version}-${commitHash}.tar"
                                    artifact_path = "https://hc-eu-west-aws-artifactory.cloud.health.ge.com/artifactory/${isotargetRepo}"
                                    deployTag = "${project_version}-${commitHash}"
                                    app_type = config.appType
                                    deploy_project_name = "${project}"
                                    deploymentFolderLocation=config.deployment_folder_location
                                    artifact_type = "${artifactType}"
                                    deployment_type = config.deploymentType

                                }
                                break
                            }
                        }
                    }
			    }
            }
        }
        catch(err){
            currentBuild.result = 'FAILURE'
            emailExtraMsg = "Build Failure:"+ err.getMessage()
            throw err
        }
        finally {
            try {
            jaas_sensor_postjob{
                    settings= hcddSettings
            }

            if(config.mailingList) {
                jaas_email {
                    mailingList="${config.mailingList}"
                    projectName="Branch:${hcddSettings.branch}:Repo:${config.project}"
                    message="$emailExtraMsg"
                    includeChangeSet=true
                }
            }
            if(config.flowdockApiToken) {
                jaas_flowdock {
                    apiToken="$config.flowdockApiToken"
                    message=emailExtraMsg
                }
            }
            } catch(err){
                currentBuild.result = 'FAILURE'
                emailExtraMsg = "Build Failure:"+ err.getMessage()
                throw err
            }finally {
                println "Finally block"
            }
        }
    }
}

def code_checkout_triggered_repo() {
   echo " Checking out code"
   def retryAttempt = 0
   retry(2) {
     if (retryAttempt > 0) {
      sleep time: 1, unit: 'MINUTES'
     }
     timeout(time: 3, unit: 'MINUTES') {
       retryAttempt = retryAttempt + 1
       timestamps {
/*NG: Disabled for testing filmer, post testing, need to enable this again*/
         step([$class: 'WsCleanup'])
         //echo "check out======GIT =========== on ${params.sourceBranch}"
		 echo " Checking out code"
         checkout scm

       }

     }
  }
 }

def sectionDisplay(title){
  println "####################################################################################"
  println "-------------- " + title + " -------------------"
  println "####################################################################################"
}

def checkFolderExists(folderPath){
    // Create a File object representing the folder 'A/B'
    def folder = new File( "${folderPath}" )

    // If it doesn't exist
    if( !folder.exists() ) {
        // Create all folders up-to and including B
        println "${folderPath} doesn't exists"
        return false
    }
    else{
        return true
    }
}
