def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def gitUrl = "${config.gitSwarmUrl}"
    echo gitUrl
    def hostip_bdd = "${config.hostip_dev_env_helm}"
    def bddOnServer = config.bddOnServer?:'false'
    println "bddOnServer ${bddOnServer}"
    def bddTargetName = config.bddTargetNodeName?:"DeploymentNode-5xIsoEmlPaas"
    println "bddTargetName ${bddTargetName}"
    def build_number = "${env.BUILD_NUMBER}".toString()
    //def project_test = "${config.projectTest}"
    def pod_namespace = config.podNamespace?:'edison-core'
    def cloud_name = config.cloudName
    def jnlp_repo = config.jnlpRepo
    def base_dir = ''
    def bdd_runner_image_repo = config.bddRunnerImageRepo
    def bdd_publisher_credentials = config.bddPublisherCredentials
    def bdd_publisher_node = config.bddPublisherNode
    def componentTestingBaseDirStructure = config.component_testing_base_dir_structure
    def componentTestingBuildDirStructure = config.component_testing_build_dir_structure
    def generalDisplayUtils = new org.utils.generalDisplayUtils()
    //def componentTestingBaseDirStructure = "/component-test"
    //def componentTestingBuildDirStructure = "/component-test/build/"
    debugMap = ['componentTestingBuildDirStructure': componentTestingBuildDirStructure,
    'componentTestingBaseDirStructure':componentTestingBaseDirStructure, '[bddTesting]build_command': "${config.build_command}", 'bddTargetName': bddTargetName]
    generalDisplayUtils.debugLines(debugMap)
    //def podTemplateCredId = config.pod_template_cred_id
    def pod_template_cred_id = config.podTemplateCredId

    //echo 'project comp test'
    //echo "${project_test}"
    def tar_destination = config.tarDestination
    def project_build_type = "${config.build_type}"
    def build_command
    if(project_build_type == 'maven') {
        if(config.build_command) {
            build_command = config.build_command
        } else {
            build_command = "mvn clean install"
        }
//        build_command = "${config.build_command}" ?: "mvn clean install"
    }
    else {
        if(config.build_command) {
            build_command = config.build_command
        } else {
            build_command = "gradle clean build --stacktrace --info"
        }
//        build_command = "${config.build_command}" ?: "gradle clean build --stacktrace --info"
    }
    debugMap = ['[bddTesting] buildCommand': "${build_command}",'cloud name': "${cloud_name}",
               'jnlp_repo': jnlp_repo, 'bdd_runner_image_repo': bdd_runner_image_repo]
    generalDisplayUtils.debugLines(debugMap)

    def slave_label = "bdd-runner-${build_number}"
    echo "slave_label = ${slave_label}"
    println "git_branch[componentTestingHelm] ${config.git_branch}"
    timeout(80) {
        if ("${bddOnServer}" == 'false'){
            println "BDD testing on pod"
            podTemplate(label: slave_label, cloud: "${cloud_name}", namespace: "${pod_namespace}", containers: [
            containerTemplate(name: 'jnlp', image: "${jnlp_repo}", ttyEnabled: true),
            containerTemplate(name: 'bdd-runner-with-dataset', image: "${bdd_runner_image_repo}" , ttyEnabled: true)]) {
                node(slave_label) {
                    git branch:"${config.git_branch}", credentialsId: pod_template_cred_id, poll: false, url:"${gitUrl}"
                    container('bdd-runner-with-dataset') {
                        echo "project_build_type ${project_build_type}"
                        base_dir = pwd(); echo base_dir
                        tar_file_name ="${build_number}"+".tar.gz"
                        component_test_dir = "${base_dir}"+"/"+ "${componentTestingBaseDirStructure}"
                        tar_file_location = "${base_dir}/${componentTestingBaseDirStructure}/${tar_file_name}"
                        build_directory = "${base_dir}/${componentTestingBuildDirStructure}/*"
                        componentTestCommandExecution(component_test_dir,build_command)
                        componentTestTarPackaging(project_build_type,component_test_dir,tar_file_location)
                        componentTestReportPublishToPublisherNode(component_test_dir,bdd_publisher_node,bdd_publisher_credentials,tar_destination)

                    } //withCredentials
                }
            }
        }
        else if ("${bddOnServer}" == 'true'){
            println "BDD testing On Server"
            node(bddTargetName) {
            git branch:"${config.git_branch}", credentialsId: pod_template_cred_id, poll: false, url:"${gitUrl}"
                echo "project_build_type ${project_build_type}"
                base_dir = pwd(); echo base_dir
                tar_file_name ="${build_number}"+".tar.gz"
                component_test_dir = "${base_dir}"+"/"+ "${componentTestingBaseDirStructure}"
                tar_file_location = "${base_dir}/${componentTestingBaseDirStructure}/${tar_file_name}"
                build_directory = "${base_dir}/${componentTestingBuildDirStructure}/*"
                componentTestCommandExecution(component_test_dir,build_command)
                componentTestTarPackaging(project_build_type,component_test_dir,tar_file_location)
                componentTestReportPublishToPublisherNode(component_test_dir,bdd_publisher_node,bdd_publisher_credentials,tar_destination)
            }
        }
        else{
            println "bddOnServer value is ${bddOnServer}"
            println "Kindly mention true/false for key bddOnServer"
        }
        script {
            def logs = currentBuild.rawBuild.getLog(10000)
            def result = logs.find { it.contains('Component test execution failed') }
            if (result) {
                error ('Failing due to ' + result)
            }
        }
     }
}


def componentTestCommandExecution(component_test_dir,build_command){
    try{
        sh """
            cd "${component_test_dir}"
            ${build_command} || echo "Component test execution failed"
        """
    }
    catch(err){
        println "Component test execution failed"
    }
}

def componentTestExecutionGradle(){
    if (fileExists("${component_test_dir}/build.gradle")) {
        componentTestCommandExecution(component_test_dir,build_command)
    }
    else {
        ansiColor('xterm') {
            println "Skipping execution of component BDD tests for now as they are not available. Please add the tests at the earliest"
        }
        System.exit(0)
    }
}


def componentTestTarPackaging(project_build_type,component_test_dir,tar_file_location){
    if( "${project_build_type}" == 'gradle'){
        sh """
        if [[ -d ${component_test_dir}/build && -d ${component_test_dir}/target ]]
        then
            cd "${component_test_dir}" && tar -cvf "${tar_file_location}" build/ target/
        else
            echo "PIPELINE_ERROR: ${component_test_dir}/target and/or ${component_test_dir}/build folder does not exist"
            exit 1
        fi
    """
    }
    else{
        sh """
        if [[ -d ${component_test_dir}/target ]]
        then
            cd "${component_test_dir}" && tar -cvf "${tar_file_location}" target/
        else
            echo "PIPELINE_ERROR: ${component_test_dir}/target folder does not exist"
            exit 1
        fi
    """
    }
}

def componentTestReportPublishToPublisherNode(component_test_dir,bdd_publisher_node,bdd_publisher_credentials,tar_destination){
    if (fileExists("${component_test_dir}")) {
        dir("${component_test_dir}") {
            withCredentials([usernamePassword(credentialsId: "${bdd_publisher_credentials}", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                sh """
                sshpass -p "$PASSWORD" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "$USERNAME"@"${bdd_publisher_node}" mkdir -p $tar_destination
                sshpass -p "$PASSWORD" scp -r -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "${build_number}".tar.gz "${USERNAME}"@${bdd_publisher_node}:"${tar_destination}"
                """
            }
        }
    }
    else{
        println "${component_test_dir} component_test_dir doesn't exist. Nothing to publish"
    }
}
