import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.lang.Binding;
import groovy.lang.GroovyShell;

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    println("common_pipeline [start]")

    if(!config.Modality || !config.version) {
        error("\"Modality\" and \"version\" parameters are mandatory in Jenkinsfile")
    }

    def modality = config.Modality.toLowerCase()
    def enableWorkspaceCleanup = false
    def emailExtraMsg="";
    def enable_stages = [ 'code_checkout' ] //, 'ALM_Integration', 'build_and_unit_test', 'sonar_analysis', 'publish_jar', 'publish_rpm', 'create_and_publish_docker', 'docker_scan', 'iso_creation', 'deployment_and_testing', 'create_app_bundle', 'git_tag']

    def allParamDict = [:]
    def debugMap
    def build_version = "" // version()
    def credID
    def ddReportDir
    def sonarQubeEnv = config.sonarQubeEnv

    if (env.BRANCH_NAME != null) {
        allParamDict["git_branch_name"] = "${env.BRANCH_NAME}"
    } else if (env.gitlabBranch != null )  {
        allParamDict["git_branch_name"] = "${env.gitlabBranch}"
    } else if (params.sourceBranch != null ){
        allParamDict["git_branch_name"] =  "${params.sourceBranch}"
    } else {
        error("No branch to build")
    }
    println " Source Branch:" + allParamDict["git_branch_name"]

    env.JAAS_LOGGER_LEVEL= "FINE"
    env.http_proxy= ''
    env.https_proxy= ''
    def hcddSettings= new hcdd.Settings()
    hcddSettings.org= 'Imaging'
    hcddSettings.team= modality
    hcddSettings.program= 'Coreload'
    hcddSettings.product= 'EdisonMachine'

    hcddSettings.branch= allParamDict["git_branch_name"]
    hcddSettings.release= '1.0.0'
    hcddSettings.component= config.project

    //**------------Constant Parameters [start]-------------------------------------*/
    def generalDisplayUtils = new org.utils.generalDisplayUtils() //** General display utility Object for log organization **
    def generalStageUtils = new org.utils.generalStageUtils() //** General stage utility Object for code reusability ease **
    def generalGroovyUtils = new org.utils.generalGroovyUtils() //** General groovy utility Object for code reusability ease **
    def build_and_publish = new org.utils.build_and_publish()
    def scmObj = new org.utils.scm() //** Scm object to make git calls  **
    def shellObj = new org.utils.shell() //** Shell Object to call linux commands / scripts **
    def current_datetime = new Date().format( 'yyMMddHHmmss' ) //**Provides current date */
    def publish_files_list = []
    def docker_info = []
    def list_of_images = []
    //**------------Constant Parameters [end]-------------------------------------*/

    println "Modality: " + modality


    def settingsYamlDict = generalStageUtils.settingsYamlDict(allParamDict["git_branch_name"], modality)
    allParamDict = allParamDict +  settingsYamlDict + config  //** Prioritywise concatenation of dictionary vaalues **
    println "allParamDict"
    println allParamDict
    def docker_artifactory_url
    def branch_type
    def isoArtifactoryRepoName
    def checkedoutCodeDetails
    def xBox_type
    def repo_name
    def code_checkout_url
    def iso_name_without_extension
    def iso_url_form
    def commitHashData
    def ret_code
    enable_stages = allParamDict["enable_stages"] ?: enable_stages
    enable_stages = enable_stages.collect{ it.toLowerCase() }
    if (settingsYamlDict["releaseBranch"].contains(allParamDict["git_branch_name"].toString()) ) {
        println("DEBUG: release branch")
        //checking the duplication of branch name, need to stop the build if it's mentoned under integBranch as well
        def isDuplicateBranch = isDuplicate(allParamDict["git_branch_name"].toString(), settingsYamlDict["integBranch"])
        println("DEBUG: is duplicate "+ isDuplicateBranch)
        if(isDuplicateBranch){
            error("Build failure: Branch name '"+ allParamDict["git_branch_name"]+ "' mentioned in multiple branch types!")
        }
        branch_type = "release"
        allParamDict['branch_type'] = branch_type
        allParamDict['buildFailOnSonarError'] = true
        //for release branch sonar_analysis is mandatory after build
/*        if(enable_stages.contains('build_and_unit_test')){
            enable_stages.add('sonar_analysis')
        }
        //for release branch docker_scan is mandatory after docker_build_and_publish
        if(enable_stages.contains('create_and_publish_docker')){
            enable_stages.add('docker_scan')
        }
*/
        allParamDict["enableWorkspaceCleanup"] = true   // for release branch, make enableWorkspace true by default
    }else if (settingsYamlDict["integBranch"] && settingsYamlDict["integBranch"].contains(allParamDict["git_branch_name"].toString())) {
        println("DEBUG: Integ branch")
        //checking the duplication of branch name, need to stop the build if it's mentoned under releaseBranch as well
        def isDuplicateBranch = isDuplicate(allParamDict["git_branch_name"].toString(), settingsYamlDict["releaseBranch"])
        println("DEBUG: is duplicate "+ isDuplicateBranch)
        if(isDuplicateBranch){
            error("Build failure: Branch name '"+ allParamDict["git_branch_name"]+ "' mentioned in multiple branch types!")
        }
        branch_type = "integ"
        allParamDict['branch_type'] = branch_type
        allParamDict['buildFailOnSonarError'] = true
        allParamDict["enableWorkspaceCleanup"] = true   //overwrite jenkinsfile value
    } else {
        println("DEBUG: Dev branch")
        branch_type = "dev"
        allParamDict['branch_type'] = branch_type
    }
    enableWorkspaceCleanup = allParamDict["enableWorkspaceCleanup"] ?: false

    def deploy_repo        = allParamDict["deploy_repo"]
    def deploy_repo_branch = allParamDict["deploy_repo_branch"] ?: allParamDict["git_branch_name"]
    def artifactory_url    = allParamDict["artifactory_url"]
    def contArgs           = allParamDict["contArgs"]
    def gitLabCredId       = allParamDict["gitlab_cred_id"]

    def maven_repo  = allParamDict[branch_type]["maven_repo"]
    def docker_repo   = allParamDict[branch_type]["docker_repo"]
    hcddSettings.pipelinePhase = allParamDict[branch_type]["pipelinePhase"]

    def buildNode     = allParamDict["buildNode"]
    currentBuild.description = "<span style=\""+allParamDict[branch_type]["desc_style"]+"\">${allParamDict["git_branch_name"]}</span>"
    def project = ''
    println("branch_type : " + branch_type )
    println("bd_color:" + allParamDict[branch_type]["bd_color"])

    def artifactory_credID_api_key = ''
    def reportDirectory_unitTest
    def checkout_folder
    def commitHash
    properties([gitLabConnection(allParamDict["gitlab_connection"])])
	echo "BUILD NODE :: $buildNode"
    node(allParamDict["buildNode"]) {
        timestamps {
            def blockBuilds = "${env.block_builds}"
            def blockedBranches = "${env.blocked_branches}"
            def server
            def rtMaven
            def buildInfo
            def manifest_file_loc
            def arch
            if(config.arch || config.arch == '') {
                arch = config.arch
            } else {
                println("build_type = " + allParamDict["buildType"])
                arch = allParamDict[allParamDict["buildType"]+"_arch"]
                println("arch to use in build : "+arch)
            }
            project = allParamDict["project"]
            def workspace = env.WORKSPACE
            try {
                gitlabCommitStatus(connection: gitLabConnection(allParamDict["gitlab_connection"]), name: allParamDict["jenkinsJobPath"]) {
                    setSleepTime(allParamDict["checkoutSleepTime"]) //** Set sleep time from Jenkinsfile **
                    String gitSwarmUrl_ssh = ''
                    String commit_hash_ci_skip= ''

                    checkout_folder = allParamDict["checkout_folder"] ?: "${env.WORKSPACE}"
                    
                    dir(checkout_folder) {
                        if(enable_stages.contains('code_checkout')){
                            stage('Code Checkout') {
                                common_code_checkout.gitLabCheckOut(allParamDict)
                            }//Code checkout stage
                        } else {
                            Utils.markStageSkippedForConditional("${env.STAGE_NAME}")
                        }
                        checkedoutCodeDetails = generalStageUtils.checkedoutCodeDetails(allParamDict["enableDebugForDSL"])
                        xBox_type = allParamDict["deployType"] ?: [] //Possible Values xBoxType = ['docker', 'helm', 'rpm', 'iso-eml', 'iso-ehl']
                        repo_name = checkedoutCodeDetails['repo_name']
                        code_checkout_url = checkedoutCodeDetails['code_checkout_url']
                        gitSwarmUrl_ssh = checkedoutCodeDetails['gitSwarmUrl_ssh']

                        commitHash = shellObj.shell_ret_output("git rev-parse --short HEAD").trim()

                        def configVersion = config.version
                        if(!(configVersion =~ /^[0-9]+\.[0-9]+\.[0-9]*$/)){
                            def dynmicVersion = sh(returnStdout: true, script: config.version)
                            println("dynamic version is : "+ dynmicVersion)
                            configVersion = dynmicVersion.trim()
                            if(configVersion =~ /^[0-9]+\.[0-9]+\.[0-9]*$/){
                                config.version = configVersion
                                println("This is dynamic version from sh script "+ configVersion)
                            }else {
                                println("version passed is not correct : "+ configVersion)

                            }
                        }

                        if(branch_type == "release") {
                            build_version = config.version
                        } else if((branch_type == "integ")) {
                            build_version = "${config.version}-${current_datetime}-master"
                        } else {
                            build_version = """${config.version}-${current_datetime}-${allParamDict["git_branch_name"]}"""
                            println("build_version "+ build_version)
                        }
                        //plug n play hook for specific ISO download
                        isoArtifactoryRepoName = allParamDict["${branch_type}"]['generic_repo']
                        println "isoArtifactoryRepoName ${isoArtifactoryRepoName}"
                        if(enable_stages.contains("iso_creation")) {
                            iso_name_without_extension = "${allParamDict['isoNames']}_${build_version}"
                        } else {
                            iso_name_without_extension = allParamDict['iso_name_without_extension']
                        }
                        allParamDict['iso_name_without_extension'] = iso_name_without_extension
                        iso_url_form = "https://${allParamDict['artifactory_url']}/artifactory/${isoArtifactoryRepoName}/${allParamDict['isoNames']}/${iso_name_without_extension}"
                        def paramFormationDict = ['dev_repo': checkedoutCodeDetails['gitSwarmUrl_ssh'], 'dev_branch': allParamDict["git_branch_name"], 'iso_url_form': iso_url_form ]
                        allParamDict = allParamDict + paramFormationDict
                    }
                    if(enable_stages.contains('alm_integration')){
                        stage('ALM Integration'){
                            common_alm_integration.executeAlmIntegration(allParamDict)
                        }
                    }//ALM Integration
                    stage("Build and Unit Test") {
                        if(enable_stages.contains('build_and_unit_test')){
                            gitlabCommitStatus(connection: gitLabConnection(allParamDict["gitlab_connection"]), name: STAGE_NAME) {
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'running')
                                if(allParamDict["updatePackageJson"]) {
                                    allParamDict['packageJsonUpdated'] = build_and_publish.updatePackageJson(config.version)
                                }
                                reportDirectory_unitTest = common_build_unit_test.buildAndUnitTest(allParamDict, arch, contArgs, maven_repo, config.version, build_version, current_datetime)
                                if(allParamDict["testReportDir"]) {
                                    bdd_publisher_node = allParamDict["bddPublisherNode"]?:'bdd_publisher'
                                    tar_file_name = "${env.BUILD_NUMBER}.tar.gz"
                                    tar_file_location = "/jenkins_workspace/${modality}/${project}/${branch_type}/${tar_file_name}"
                                    bddPublisherParentDir = "/jenkins_workspace/${modality}/${project}"
                                    bddPublishDir = "/jenkins_workspace/${modality}/${project}/${branch_type}"
                                    withCredentials([usernamePassword(credentialsId: allParamDict['bddPublisherCredentials'], usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

                                        sh """cd $WORKSPACE/ && tar -cvf "${tar_file_name}" ${allParamDict["testReportDir"]}"""
                                        sh """sshpass -p ${PASSWORD} ssh ${USERNAME}@${bdd_publisher_node} find ${bddPublisherParentDir} -maxdepth 1 -type f -delete"""
                                        sh """sshpass -p ${PASSWORD} ssh ${USERNAME}@${bdd_publisher_node} mkdir -p ${bddPublishDir}"""
                                        sh """sshpass -p ${PASSWORD} scp -r -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null $tar_file_name ${USERNAME}@${bdd_publisher_node}:${bddPublishDir}"""
                                    }
                                }
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'success')
                            }
                        }//enable stage Build and unit-test
                        else{
                            Utils.markStageSkippedForConditional("${env.STAGE_NAME}")
                        }
                    }//stage Build and Unittest
                    stage("Sonar Analysis") {
                        gitlabCommitStatus(connection: gitLabConnection(allParamDict["gitlab_connection"]), name: STAGE_NAME) {
                            if (enable_stages.contains('sonar_analysis')) {
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'running')
                                credID = generalStageUtils.getArtifactoryCredentialAPIKey(artifactory_url, settingsYamlDict, 'api_key')
                                ret_code = generalStageUtils.exec_hook(allParamDict["sonar_analysis_prestep"])
                                if (ret_code != 0) {
                                    error("sonar_analysis_prestep failed")
                                }

                                reportFileLocation = findFiles(glob: '**/build/reports/jacoco/test/jacocoTestReport.xml')

                                sonar_scan {
                                    branch_name = allParamDict["git_branch_name"]
                                    sonar_arch = allParamDict["sonar_arch"] ?: ''
                                    contArgs1 = contArgs
                                    sonarQubeEnv = allParamDict["sonarQubeEnv"]
                                    sonarPropertiesFile = allParamDict["sonarPropertiesFile"] ?: "sonar.properties"
                                    sonarScannerPath = allParamDict["sonar_scanner_path"]
                                }

                                //if(reportFileLocation==null){
                                    reportFileLocation = findFiles(glob: '**/report-task.txt')
                              //  }
                                if (reportFileLocation.length > 0) {
                                    ddReportDir = reportFileLocation[0].toString()
                                } else {
                                    ddReportDir = ""
                                }
                                println("ddReportDir : " + ddReportDir + " :")
                                jaas_sonar {
                                    reportFile = "${ddReportDir}"
                                    sonarUserCredential = 'sonar_prod_token'
                                    failBuild = allParamDict['buildFailOnSonarError'] ?: false
                                    settings = hcddSettings
                                    sonarTimeout = 5
                                }
                                if (allParamDict['branch_type'] == 'release') {
                                    sonar_reports_pdf {
                                        sonarProjectName = allParamDict["project"]
                                        sonarProjectKey = allParamDict["sonarProjectKey"]
                                        sonar_cmd = allParamDict["sonar_cmd"]
                                        arch1 = allParamDict["pdfconverter_arch"]
                                        contArgs1 = allParamDict["contArgs"]
                                        sonarQubeEnv = allParamDict["sonarQubeEnv"]
                                        branch_name = allParamDict["git_branch_name"]
                                        timestamp = current_datetime
                                        sonarHostUrl = allParamDict["sonarHostUrl"]
                                        artifactory_url = artifactory_url
                                        artifactory_repo = allParamDict[branch_type]["generic_repo"]
                                        modality_name = allParamDict["Modality"]
                                        credentialsId = credID
                                    }
                                }

                                ret_code = generalStageUtils.exec_hook(allParamDict["sonar_analysis_poststep"])
                                if (ret_code != 0) {
                                    error("sonar_analysis_poststep failed")
                                }

                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'success')
                            }//enable_stages sonar analysis
                            else {
                                Utils.markStageSkippedForConditional("${STAGE_NAME}")
                            }
                        }
                    }//Sonar analysis

                    // Publish Artifacts
                    if(enable_stages.contains('publish')){
                        stage("Publish") {
                            gitlabCommitStatus(connection: gitLabConnection(allParamDict["gitlab_connection"]), name: STAGE_NAME) {
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'running')
                                if(allParamDict['publishType'] != null && (allParamDict['publishType'].size() != 1  || allParamDict['publishType'].get("maven") == null) ){
                                    println("Not a single maven publish hence executing prestep")
                                    ret_code = generalStageUtils.exec_hook(allParamDict["publish_prestep"])
                                    if(ret_code != 0) {
                                        error("publish_prestep failed")
                                    }
                                }
                                // In case publish type is single, user may give it as string instead of array.
                                // In that case, convert it to list.
                                if (allParamDict['publishType'] instanceof String) {
                                    println("single publish")
                                    def tst = []
                                    tst.add(allParamDict['publishType'])
                                    allParamDict['publishType'] = tst
                                }

                                if(!allParamDict['publish_path']?.trim()) {
                                    allParamDict['publish_path'] = allParamDict["project"]
                                }
                                if(allParamDict['publishType'] == null && allParamDict["buildType"] == "maven"){
                                    allParamDict['publishType'] = ["maven": ""]
                                }
                                allParamDict['publishType'].each { key, value ->
                                    key = key.toLowerCase()
                                    println("publishType: "+key)
                                    println("filePattern: "+value)
                                    if(key == 'maven') {
                                        stage("Publish Maven") {
                                            credID = generalStageUtils.getArtifactoryCredentialAPIKey(artifactory_url, settingsYamlDict, 'username_passwd')
                                            def publishExcludePattern = allParamDict['publishType'].get("exclude")
                                            build_and_publish.maven_publish (arch, contArgs, maven_repo, commitHash, artifactory_url, credID, allParamDict["project"], value, publishExcludePattern , allParamDict['mvnBuildDetails'], allParamDict["publish_prestep"], workspace)
                                        }
                                    } else if(key =='gradle') {
                                        stage("Publish Gradle") {
                                            credID = generalStageUtils.getArtifactoryCredentialAPIKey(artifactory_url, settingsYamlDict, 'username_passwd')
                                            build_and_publish.gradle_publish (arch, contArgs, artifactory_url, maven_repo, maven_repo, allParamDict["gradleVersion"], credID, allParamDict["gradleCustomPublishCmd"]?:' ', commitHash)
                                        }
                                    } else if(key in ['generic' , 'yum', 'pypi', 'npm'] ) {
                                        println("generic-publish.")
                                        stage("Publish "+ key) {
                                            credID = generalStageUtils.getArtifactoryCredentialAPIKey(artifactory_url, settingsYamlDict, 'api_key')
                                            publish_files_list = build_and_publish.generic_publish (artifactory_url, allParamDict[branch_type], key, allParamDict['publish_path'], allParamDict['dynamic_publish_path'],  allParamDict['follow_source_path'], workspace, value, credID, commitHash)
                                        }
                                    } else {
                                        println("PIPELINE_ERROR:  Unknown publish type")
                                    }
                                } // each publish type
                                ret_code = generalStageUtils.exec_hook(allParamDict["publish_poststep"])
                                if(ret_code != 0) {
                                    error("publish_poststep failed")
                                }
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'success')
                            }
                        }
                    }//Stage publish
                    if(enable_stages.contains('create_and_publish_docker')){
                        stage("Create and Publish Docker") {
                            println("artifactoryurl:" + settingsYamlDict["artifactory_url"])
                            credID = generalStageUtils.getArtifactoryCredentialAPIKey(settingsYamlDict["artifactory_url"], settingsYamlDict, 'username_passwd')
                            build_and_publish.docker_build(settingsYamlDict["docker_build_publish_arch"], contArgs, allParamDict["dockerFiles"], settingsYamlDict["artifactory_url"], docker_repo, allParamDict["version"], project, list_of_images, commitHash, build_version,allParamDict["create_and_publish_docker_prestep"],"")
                            stage("Docker Image Scan") {
                                if(enable_stages.contains('docker_scan')){
                                    sh "cat anchore_images"
                                    anchore bailOnFail: false, engineRetries: '1200', name: 'anchore_images'
                                }//docker_scan
                                else{
                                    Utils.markStageSkippedForConditional("${STAGE_NAME}")
                                }
                            }//Docker Image Scan
                            stage("Docker Hardening") {
                                if(enable_stages.contains('docker_hardening')){
                                    def secOpsUtils = new org.utils.secOpsUtils()
                                    secOpsUtils.docker_hardening("${image}","${build_version}", allParamDict["dsl_repo"], allParamDict["gitlab_cred_id"], allParamDict["hardening_score"], '','')
                                }
                                else{
                                    Utils.markStageSkippedForConditional("${STAGE_NAME}")
                                }//docker_hardening
                            }//docker_hardening stage
                            docker_info = build_and_publish.docker_publish(settingsYamlDict["docker_build_publish_arch"],contArgs,settingsYamlDict["artifactory_url"],list_of_images,credID,"",allParamDict["create_and_publish_docker_poststep"])
                        }//create_and_publish_docker
                    } // docker_create_and_publish

                    // Update and push package.json to Git repo
                    if(allParamDict["packageJsonUpdated"]) {
                        build_and_publish.checkPackageJsonUpdate(config.version, allParamDict["git_branch_name"])
                    }

                    if(enable_stages.contains('update_iso_manifest')){
                        stage ('Update ISO Manifest'){
                            gitlabCommitStatus(connection: gitLabConnection(allParamDict["gitlab_connection"]), name: STAGE_NAME) {
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'running')

                                ret_code = generalStageUtils.exec_hook(allParamDict["update_iso_manifest_prestep"])
                                if (ret_code != 0) {
                                    error("update_iso_manifest_prestep failed")
                                }
                                if (allParamDict["checksumManifestUpdate"]) {
                                    def publish_info
                                    if (publish_files_list) {
                                        publish_info = publish_files_list
                                    }
                                    if (docker_info) {
                                        if (publish_info) {
                                            docker_info.each{
                                                publish_info = publish_info + it
                                            }
                                        } else {
                                            publish_info = docker_info
                                        }
                                    }
                                    println(publish_info)
                                    updateIsoManifestYaml {
                                        eesDeployCheckoutBranch = deploy_repo_branch
                                        group_name_iso = allParamDict["isoGroupName"]
                                        repoName = allParamDict["project"]
                                        gitSwarmUrlSsh = gitSwarmUrl_ssh
                                        commit_hash = commitHash
                                        deployRepo = deploy_repo
                                        currentDate = current_datetime
                                        files_from_publish = publish_info
                                        helm_chart_folder = 'NA'
                                        commit_hash_ci_skip = 'NA'
                                        manifestFileLoc = 'NA'
                                        checksumManifestUpdate = allParamDict["checksumManifestUpdate"]
                                    }//updateIsoManifestYaml
                                } else {
                                    //** ------------------------Update Additional values yaml before update iso manifest [BEGINS]----------------------- **
                                    docker_artifactory_url = "${settingsYamlDict["artifactory_url"]}" + '/' + "${docker_repo}" + '/' + project + '/' + "${project}"
                                    println "update_iso_manifest git_branch_name " + "${allParamDict["git_branch_name"]}"
                                    credID = generalStageUtils.getArtifactoryCredentialAPIKey(docker_artifactory_url, settingsYamlDict, 'username_passwd')
                                    lock('ISOManifestUpdate') {
                                        println "Lock for updateIsoManifestYaml Region Starts"
                                        saasCreateAdditionalValues {
                                            helm_chart_location = allParamDict["source_deployment_folder"]
                                            helm_app_release = commitHash
                                            helm_app_version = build_version
                                            manifestFolderLocation = allParamDict["manifest_folder"]
                                            dockerImageLocation = docker_artifactory_url
                                            dockerImageTag = "${build_version}"
                                            additionalValuesFileLocation = pwd() + '/' + allParamDict["source_deployment_folder"] + '/values.yaml'
                                            gitBranchForPush = allParamDict["git_branch_name"]
                                            gitSwarmUrl = gitSwarmUrl_ssh
                                            credentialsId = credID
                                            artifact_url = artifactory_url
                                        }//saasCreateAdditionalValues

                                        commitHashData = readYaml file: ('commitHash.yaml')
                                        commit_hash_ci_skip = commitHashData['commit_hash_ci_skip']

                                        def list_of_manifest_files = sh returnStdout: true, script: """ls ${allParamDict["manifest_folder"]}/*-manifest.yaml"""
                                        //    if (allParamDict["multiple_iso"] == 'true'){
                                        list_of_manifest_files.split('\n').each {
                                            manifest_file_loc = it
                                        }//list_of_manifest_files
                                        //    }//mupltiple_iso_existence
                                        echo "manifest file path is ${manifest_file_loc}"
                                        println "deploy_repo " + deploy_repo
                                        println "repo_name " + repo_name

                                        updateIsoManifestYaml {
                                            commitHashCiSkip = commit_hash_ci_skip
                                            sourceDeploymentFolder = allParamDict["source_deployment_folder"]
                                            eesDeployCheckoutBranch = deploy_repo_branch
                                            group_name_iso = allParamDict["isoGroupName"]
                                            repoName = allParamDict["project"]
                                            gitSwarmUrlSsh = gitSwarmUrl_ssh
                                            commit_hash = commitHash
                                            manifestFileLoc = manifest_file_loc
                                            deployRepo = deploy_repo
                                            currentDate = current_datetime
                                        }//updateIsoManifestYaml
                                        println "Lock for updateIsoManifestYaml Region Ends"
                                    }// sequential Stage update_iso_manifest
                                } // Not checksumManifestUpdate
                                ret_code = generalStageUtils.exec_hook(allParamDict["update_iso_manifest_poststep"])
                                if (ret_code != 0) {
                                    error("update_iso_manifest_poststep failed")
                                }
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'success')
                            }
                        }
                    }// enable stages contains update_iso_manifest

                    if (enable_stages.contains("iso_creation")){
                        ret_code = generalStageUtils.exec_hook(allParamDict["enableisoCreation_prestep"])
                        if(ret_code != 0) {
                            error("enableisoCreation_prestep failed")
                        }
                        println "enableisoCreation: deployRepoBranch: "+ deploy_repo_branch
                        println "isoVersion: " + build_version
                        credID = generalStageUtils.getArtifactoryCredentialAPIKey(artifactory_url, settingsYamlDict, 'username_passwd')
                        isoCreationAndPublish {
                            modality_name = allParamDict["Modality"]
                            deployRepo = deploy_repo
                            deployRepoBranch = deploy_repo_branch
                            iso_names = allParamDict["isoNames"]
                            forced_build = allParamDict["forcedBuild"] ?: 'true'
                            publish_iso_cred_id = credID
                            git_lab_cred_id = gitLabCredId
                            iso_artifactory_repo_name = allParamDict[branch_type]["generic_repo"]
                            use_promoted_artifacts_only = allParamDict["usePromotedArtifacts"] ?: 'false'
                            iso_version = build_version
                            artifactoryUrl = artifactory_url
                            currentDate = current_datetime
                            commit_hash = commitHash
                        }//isoCreationAndPublish
                        ret_code = generalStageUtils.exec_hook(allParamDict["enableisoCreation_poststep"])
                        if(ret_code != 0) {
                            error("enableisoCreation_poststep failed")
                        }
                    }//config.isoCreation
                    if ((enable_stages.contains('update_master_release_xml')) && !("${allParamDict["git_branch_name"]}".contains('devops'))){
                        stage('Update Master Release Xml'){
                            ret_code = generalStageUtils.exec_hook(allParamDict["update_master_release_xml_prestep"])
                            if(ret_code != 0) {
                                error("update_master_release_xml_prestep failed")
                            }
                            updateMasterReleaseXml {
                                iso_name = allParamDict["isoNames"]
                                deployRepoBranch = deploy_repo_branch
                                project_Name = project
                                group_Name = allParamDict["groupName"]
                                iso_names = allParamDict["isoNames"]
                                artifactory_api_token_cred_id = allParamDict["artifactoryApiTokenCredId"]
                                deploy_repo = allParamDict["deploy_repo"]
                                publish_iso_cred_id = allParamDict["publishIsoCredId"]
                                git_lab_cred_id = gitLabCredId
                                generic_repo = allParamDict[branch_type]["generic_repo"]
                                iso_version = build_version
                            }
                            ret_code = generalStageUtils.exec_hook(allParamDict["update_master_release_xml_poststep"])
                            if(ret_code != 0) {
                                error("update_master_release_xml_poststep failed")
                            }
                        }//stage('Update Master Release Xml')
                        /*else{
                            Utils.markStageSkippedForConditional("${env.STAGE_NAME}")
                        }*/
                    }
                    if (enable_stages.contains('create_app_bundle')){
                        println("create_app_bundle starting...")
                        stage("Publish Helm Chart"){
                            gitlabCommitStatus(connection: gitLabConnection(allParamDict["gitlab_connection"]), name: STAGE_NAME) {
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'running')
                                credID = generalStageUtils.getArtifactoryCredentialAPIKey(artifactory_url, settingsYamlDict, 'api_key')
                                packageAndPublishHelmChart{
                                    helmChartLocation = allParamDict["source_deployment_folder"]
                                    nname_of_project = project
                                    gitUrl = gitSwarmUrl_ssh
                                    testEnablerChart = testEnablerChartLocation
                                    customConfigLocation = configJsonArtLocation
                                    modalityName = modality
                                    helmRepo = allParamDict["helm_repo"]
                                    git_branch_name = allParamDict["git_branch_name"]
                                    artifact_url = allParamDict["artifactory_url"]
                                    artifactory_credID = credID
                                }
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'success')
                            }
                        }
                        stage('App Bundle Packaging'){
                            gitlabCommitStatus(connection: gitLabConnection(allParamDict["gitlab_connection"]), name: STAGE_NAME) {

                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'running')
                                def helmChartFolder = allParamDict["source_deployment_folder"]
                                def chartYamlContent = readYaml file:"${helmChartFolder}/Chart.yaml"
                                def helm_package_name = chartYamlContent["name"] + "-" + chartYamlContent["version"] + ".tgz"
                                def helm_package_artifactory_url =  "https://${artifactory_url}/artifactory/helm-ees-all/${chartYamlContent["name"]}/${helm_package_name}"
                                println(allParamDict["helm_repo"])
                                appBundlePackaging {
                                    registrationYamlPath       = config.registration_yaml_path
                                    appBundleManifestPath      = config.app_bundle_manifest_path
                                    chart_name                 = chartYamlContent["name"]
                                    chart_version              = chartYamlContent["version"]
                                    image_reference_list       = config.imageReferenceList
                                    application_chart_location = helmChartFolder
                                    app_bundle_chart_folder    = config.appBundleChartFolder
                                    //appBundleArtifactoryPath   = project
                                    git_branch_name            = allParamDict["git_branch_name"]
                                    branch_type                = branch_type
                                    helm_repo                  = allParamDict["helm_repo"]
                                    artifact_url               = allParamDict["artifactory_url"]
                                    gitlab_cred_id             = allParamDict["gitlab_cred_id"]
                                    deployRepo                 = config.deployRepo
                                    deployRepoBranch          = config.deployRepoBranch ?: allParamDict["git_branch_name"]
                                    yq_arch                    = allParamDict["yq_arch"]
                                    artifactory_credID         = credID
                                }
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'success')
                            }
                        }
                    }
                    //** ------------------------- Section: DEPLOYMENT [BEGINS]  -------------------------------------------- */
                    if (enable_stages.contains('deployment_and_testing')){
                        stage('deployment and testing') {
                            gitlabCommitStatus(connection: gitLabConnection(allParamDict["gitlab_connection"]), name: STAGE_NAME) {
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'running')
                            ret_code = generalStageUtils.exec_hook(allParamDict["deployment_prestep"])
                            if(ret_code != 0) {
                                error("deployment_prestep failed")
                            }
                            generalDisplayUtils.sectionDisplay("Deployment Starts", "h1")
                            generalDisplayUtils.sectionDisplay("Parallel stages for Various deployment types", "h2")
                            credID = generalStageUtils.getArtifactoryCredentialAPIKey(docker_artifactory_url, settingsYamlDict, 'username_passwd')
                            allParamDict['credID_passwd'] = credID
                            parallelXboxDeployement{
                                xBoxType = xBox_type
                                all_param_dict = allParamDict
                            }
                            ret_code = generalStageUtils.exec_hook(allParamDict["deployment_poststep"])
                            if(ret_code != 0) {
                                error("deployment_poststep failed")
                            }
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'success')
                            }
                        }
                    }
                //** ------------------------- Section: DEPLOYMENT [ENDS]  ---------------------------------------------- */
                    if (enable_stages.contains("git_tag")) {
                        stage('Git Tag') {
                            ret_code = generalStageUtils.exec_hook(allParamDict["git_tag_prestep"])
                            if(ret_code != 0) {
                                error("git_tag_prestep failed")
                            }
                            def tag_name = config.tag_name ?: "${build_version}"
                            sh "git tag -a ${tag_name} ${commitHash} -m 'CI Tag applied in ${allParamDict["git_branch_name"]}' && git push origin ${tag_name}"
                            ret_code = generalStageUtils.exec_hook(allParamDict["git_tag_poststep"])
                            if(ret_code != 0) {
                                error("git_tag_poststep failed")
                            }
                        }
                    }
                }
            }catch(err){
                currentBuild.result = 'FAILURE'
                emailExtraMsg = "Build Failure:"+ err.getMessage()
                throw err
            }//catch
            finally{
				println("finally block...")
				if ( currentBuild.result == 'FAILURE') {
					echo " ${emailExtraMsg} "
				}//if
				try {
                   jaas_sensor_postjob{
                        settings= hcddSettings
                   }
                    // Remove the docker images created
                    if(enable_stages.contains('build_and_unit_test'))  {
                        if ((reportDirectory_unitTest != null) && fileExists("${reportDirectory_unitTest}")) {
                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "${reportDirectory_unitTest}", reportFiles: 'index.html', reportName: 'Unit Test case Results', reportTitles: ''])
                        } else {
                            println ("No Unit Test report for publish ")
                        }
                    }
                    if(enable_stages.contains('create_and_publish_docker')){
                        for (images in list_of_images){
                            image_latest = images.split(":")[0]
                            sh """
                                docker rmi ${images} || echo "docker image may not be available, not an error"
                                docker rmi ${image_latest}:latest || echo "docker image may not be available, not an error"
                                echo "image deleted (if any)"
                            """
                        }
                    }
                    echo 'project parameters for testing'
                    echo project
                    if(allParamDict["testReportDir"]) {
                        bdd_publisher_node = allParamDict["bddPublisherNode"]?:'bdd_publisher'
                        node (bdd_publisher_node) {
                            stage ('Publish BDD Report') {
                                echo 'Script to publish report'
                                publishDir = "/jenkins_workspace/${modality}"
                                def tar_file_location = "${publishDir}/${project}/${branch_type}/${env.BUILD_NUMBER}.tar.gz" // Have this variable in settings.yaml
                                def untarred_location = "${publishDir}/${project}/${branch_type}/${env.BUILD_NUMBER}"
                                println("tar_file_location "+ tar_file_location)
                                println("untarred location "+ untarred_location)
                                sh """
                                    mkdir -p "${untarred_location}"
                                    tar -xvf "${tar_file_location}" -C "${untarred_location}"
                                """
                                String reportDirectory
                                String reportFileName
                                reportDirectory = "${untarred_location}" + "/" + "${allParamDict["testReportDir"]}"
                                println("reportDirectory  "+ reportDirectory)
                                reportFileName = 'index.html'
                                echo reportDirectory
                                def reportDirectoryExists = fileExists("${reportDirectory}")
                                println("reportDirectoryExists "+ reportDirectoryExists)
                                if (reportDirectoryExists) {
                                    echo 'reportDirectory is'
                                    echo reportDirectory
                                    //code to publish BDD test  metrics to HCDD
                                    def bdd_json_report_file_exists = sh(returnStdout: true, script: "find ${untarred_location}/${allParamDict["testReportDir"]} -name *.json").toString()
                                    if (!bdd_json_report_file_exists?.trim()) {
                                        println("No BDD Json Report file present to publish metrics to HCDD")
                                    }
                                } else {
                                    reportDirectory = "${untarred_location}" + "/" + "${allParamDict["testReportDir"]}"
                                    //reportFileName = 'component-test.html'
                                    reportFileName = 'index.html'
                                    echo 'reportDirectory is'
                                    echo reportDirectory
                                }
                               publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "${reportDirectory}", reportFiles: "${reportFileName}", reportName: 'Service BDD Testing Results', reportTitles: ''])

                            }
                        }
                    }
                    if (enable_stages.contains('notify')){
                        stage('Notify') {
                            gitlabCommitStatus(connection: gitLabConnection(allParamDict["gitlab_connection"]), name: STAGE_NAME) {
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'running')
                                ret_code = generalStageUtils.exec_hook(allParamDict["notify_prestep"])
                                if (ret_code != 0) {
                                    error("notify_prestep failed")
                                }
                                if(allParamDict["mailingList"]) {
                                    jaas_email {
                                        mailingList="${allParamDict["mailingList"]}"
                                        projectName="${project}"
                                        message="$emailExtraMsg"
                                        includeChangeSet=true
                                    }
                                }
                                println("MS Teams Webhook URL> "+settingsYamlDict["msteams_channel_url"])
                                if(settingsYamlDict["msteams_channel_url"]){
                                    notifyTeams(settingsYamlDict["msteams_channel_url"], shellObj, currentBuild.result)
                                }
                                ret_code = generalStageUtils.exec_hook(allParamDict["notify_poststep"])
                                if(ret_code != 0) {
                                    error("notify_poststep failed")
                                }
                                updateGitlabCommitStatus(name: STAGE_NAME, state: 'success')
                            }//EO updateGitlab
                        }
                    }
                } catch(err){
                    currentBuild.result = 'FAILURE'
                    emailExtraMsg = "Build Failure:"+ err.getMessage()
                    throw err
                }
                finally {
                    if (enableWorkspaceCleanup) {
                        step([$class: 'WsCleanup'])
                    }
                    println("common_pipeline [end]")
                }
            } // Finally block
        } // timestamps
    }//node
}//call

def version() {
    return '1.0.0'
}

//** ------------------------INDEPENDENT CAN BE REUSED FOR ANY CODE {Required to me moved to out} ===[BEGINS]==== ----------------------- **
def debugLines(debugMap, enableDebugForDSL){
    if(enableDebugForDSL == 'true'){
        debugMap.findAll { key, value ->
            println "Debug lines: " + key + " is " + value
        }
    }
}


def setSleepTime(delay){
    def sleepTime = 20
    if (delay){
        sleepTime = "${delay}".toInteger()
    }
    return sleepTime
}

def checkedoutCodeDetails(shellObj,enableDebugForDSL){
    code_checkout_url = shellObj.shell_ret_output("git config --local remote.origin.url")
    repo_name = code_checkout_url.split('/')[-1].split('\\.')[0]
    gitSwarmUrl_ssh = shellObj.shell_ret_output("git config remote.origin.url")
    debugMap = [code_checkout_url, gitSwarmUrl_ssh]; debugLines(debugMap, enableDebugForDSL)
    repoDetails = ["code_checkout_url": code_checkout_url,"repo_name": repo_name, "gitSwarmUrl_ssh": gitSwarmUrl_ssh]
    return repoDetails
}

//Check if the branch name already mentioned in configuraiton for releaseBranch/integBranch
def isDuplicate(branchName, branches) {
    if(branches){
        return branches.contains(branchName)
    }else{
        return false
    }

}

def notifyTeams(webhookUrl, shellObj, result){
     println("Sending the notificaitons to the MS Teams channel")
     try{
         def buildStatus = "SUCCESS"
         def themeColor = "00FF00"
         if (result == 'FAILURE') {
             buildStatus = "FAILURE"
             themeColor = "FF0000"
         }
         def cardbody = """ '{
                \"@type\": \"MessageCard\",
                \"@context\": \"http://schema.org/extensions\",
                \"themeColor\": \"${themeColor}\",
                \"summary\": \"${env.JOB_NAME}\",
                \"sections\": [{
                    \"activityTitle\": \"Notification from ${env.JOB_NAME}\",
                    \"activitySubtitle\": \"Latest status of build #${env.BUILD_NUMBER}\",
                    \"facts\": [{
                        \"name\": \"Status\",
                        \"value\": \"${buildStatus}\"
                    }],
                    \"markdown\": true
                }],
                \"potentialAction\":[{
                    \"@type\": \"OpenUri\",
                    \"name\": \"View Build\",
                    \"targets\": [{
                        \"os\": \"default\",
                        \"uri\": \"${env.BUILD_URL}\"
                    }]  
                }]
            }' """
        
         def requestBody = "'{\"text\" : \"Incoming test notification from Jenkins common-pipeline\"}'"
         def curlCmd1 = """curl -H "Content-Type: application/json" -d ${cardbody} -X POST ${webhookUrl}"""
        
         def proxyVar = "export https_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80;export http_proxy=http://cis-india-pitc-bangalore.corporate.ge.com:80;export no_proxy=127.0.0.1,localhost,.ge.com;"
         response_code = shellObj.shell_ret_output(proxyVar+curlCmd1) 

         println(response_code)
         println("Notificaitons to the MS Teams channel has been sent!")
    }catch(err){ 
        println "PIPELINE_ERROR: MS Teams notificaiton failed :  " + err.getMessage()  
    }
}
// End of file
