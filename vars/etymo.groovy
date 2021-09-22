def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def emailExtraMsg="";
    buildnode=config.buildNode?:'eis-devops'
    
    env.JAAS_LOGGER_LEVEL= 'FINE'
    env.http_proxy= ''
    env.https_proxy= ''
    def hcddSettings= new hcdd.Settings() 
    hcddSettings.org= 'GE Healthcare' 
    hcddSettings.team= 'Coreload-EIS' 
    hcddSettings.program= 'EIS Platform Services' 
    hcddSettings.product= 'EIS' 
    hcddSettings.branch= "${env.gitlabBranch}"
    hcddSettings.release= '1.0.0' 
    hcddSettings.component= "${env.JOB_NAME}" 
    hcddSettings.pipelinePhase= 'DEV'
    def git_branch_name = "${env.gitlabBranch}"
    def test_head = "${config.test_head}".toString()
    def request_head = "${config.request_head}".toString()
    def build_id = "${env.BUILD_ID}"
    def input_files_flag = []
    def feature_file_locations = []
    def requirement_action = "${config.requirement_action_ALM}"
    def etymoExtraParameters = "${config.etymo_extra_parameters}"
    def lastCommitMessage = []
     lastCommitMessage = config.commitMessage
    node("${buildnode}") { 
        
        feature_file_correct_location = "${config.feature_file_loc}"
        baseDir = pwd()
        echo baseDir
        def etymo_log_location = "${baseDir}" + '/etymo_logs/' + "${build_id}"
        dir(etymo_log_location){
            echo 'etymo log location'
            echo pwd()
        }
        def etymo_log_location_str = etymo_log_location.toString()
        
        jaas_sensor_step{ 
            name= "Code Checkout" 
            settings= hcddSettings 
            jaas_step={
                code_checkout()
            }
        }
        withCredentials([usernamePassword(credentialsId: 'a1473631-eada-471e-9051-1b2e7a43eeec', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            dir ("${baseDir}/json_template") {
                git branch: 'master', changelog: false, credentialsId: 'root-user-docker06', poll: false, url: 'git@gitlab-gxp.cloud.health.ge.com:Edison-Imaging-Service/etymo-docker.git'
            }
            
            //lastCommitMessage.split('\n').each {
                for( item in lastCommitMessage){
                println item
                feature_file_full_path = "${baseDir}" +'/' + item.trim()
                println 'feature_file_full_path'
                println feature_file_full_path
                if (fileExists(feature_file_full_path)) {
                    echo "001"
                    feature_file_correct_absolute_location = '"' + "${feature_file_full_path}" + '"'
                    echo "feature_file_correct_absolute_location ${feature_file_correct_absolute_location}"
                    echo "feature_file_correct_location is ${feature_file_correct_location}"
                        
                    if(feature_file_correct_absolute_location.contains(feature_file_correct_location)){
                        echo "002"
                        feature_file_locations << feature_file_correct_absolute_location
                        input_files_flag << true
                    }
                }                
            }
            def feature_json_file_location = "${baseDir}/json_template/templates/register_requirements.json"
            def parsed_json_data = readJSON file: "${feature_json_file_location}"
            parsed_json_data["inputFilesFlag"] = input_files_flag
            parsed_json_data["inputFiles"] = feature_file_locations
            parsed_json_data["almtesthead"] = test_head.toString().toInteger()
            parsed_json_data["almreqhead"] = request_head.toString().toInteger()
            parsed_json_data["output"] = etymo_log_location_str
            parsed_json_data = parsed_json_data
            writeJSON file: feature_json_file_location, json: parsed_json_data

            def trimmed_json = readFile(feature_json_file_location).replaceAll('\\"\\\\', "").replaceAll('\\\\\\"', "")
            writeFile file: feature_json_file_location, text: trimmed_json
            sh """
                echo '<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>'
                cat ${feature_json_file_location}
                echo '<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>'
            """
            
            def arch = 'hc-eu-west-aws-artifactory.cloud.health.ge.com/docker-snapshot-eis/etymo/etymo:v18.2.0.3160956'
            def contArgs = '''-it -u 0:0 -v /dockerspace:/dockerspace:rw --net=host '''    
           withDockerContainer(args: "$contArgs", image: "$arch") {
                def etymo_version = sh returnStdout: true, script: """ /usr/bin/etymo/EtymoCLI --version """
                echo etymo_version
                sh """
                    /usr/bin/etymo/EtymoCLI --alm_password=${PASSWORD} --from-json-file=${feature_json_file_location} --config-dir=/usr/bin/etymo/ ${etymoExtraParameters}
                    cat ${etymo_log_location_str}/fileLog.txt
                """
            }
                
            echo 'feature_file_locations'
            println feature_file_locations
            sh 'git checkout master'
            sh 'git status'
            feature_file_locations.each{
                def add_message = sh returnStdout: true, script: """ git add ${it} """
            }
            gitPush{
                commit_hash_map_file = 'etymoCommit.yaml'
                commitMessage = "[ci-skip]"
                gitPushBranch = 'master'
            }
        }
    }
}

def code_checkout() {
    timestamps {
        step([$class: 'WsCleanup'])
        echo "check out======GIT =========== on ${env.gitlabBranch}"
        checkout scm
    }
}
