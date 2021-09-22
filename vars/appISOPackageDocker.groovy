def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    println ("appISOPackageDocker [start]")
    def app_iso_location_docker = "${config.appISOLocationDocker}" +'/.'
    echo app_iso_location_docker
    try{
        // Assumption: name of the additional values file is: additional_values.yaml
        def additional_values_file_name = 'additional_values.yaml'
        def group = "${config.group_name}"
        def iso_manifest_absolute_location = sh(returnStdout: true, script: """ find . -name ${group}.yaml """).trim()
        //def iso_manifest_absolute_location = "/${group}.yaml"
        String buildPromotedArtifactsIso = "${config.promotedArtifacts}"
        def iso_manifest_file_parse = (!iso_manifest_absolute_location?.trim()) ? error("ISO group name is null or empty"): readYaml (file:iso_manifest_absolute_location)
        if (iso_manifest_file_parse.keySet()) {
            iso_manifest_file_parse.keySet().each {
                sh "rm -rf ${it}"
                dir(it){
                    def repo_to_clone = iso_manifest_file_parse[it]['repo_url']
                    def isPromoted = iso_manifest_file_parse[it]['promoted']
                    def additional_values_relative_location = iso_manifest_file_parse[it]['helm_chart_location']
                    def additional_values_file_path = "${additional_values_relative_location}/${additional_values_file_name}"
                    def additional_values_commit_hash
                    println "${buildPromotedArtifactsIso}"
                    additional_values_commit_hash = iso_manifest_file_parse[it]['commit_id_additional_values']
                    if ("${buildPromotedArtifactsIso}" == 'true'){
                        if (isPromoted == 'yes'){
                            additional_values_commit_hash = iso_manifest_file_parse[it]['promotion-commitid-additional-values-yaml']
                        }
                        else{
                            throw new Exception("**********************${it} is not promoted. Failing the job**********************")
                        }
                    }
                    checkout ( [$class: 'GitSCM', branches: [[name: "${additional_values_commit_hash}" ]], userRemoteConfigs: [[
                        credentialsId: 'ssh-Jenkins-s02',
                        url: "${repo_to_clone}"
                    ]]])

                    def additional_values_parsed = readYaml file: "${additional_values_file_path}"
                    def additional_values_file = readFile additional_values_file_path
                    def additional_values_file_lines = additional_values_file.readLines()
                    echo 'break001'
                    println additional_values_file_lines
                    def counter = 0
                    def image_entry_array = []
                    additional_values_file_lines.each {
                        if(it.contains('-artifactory.cloud.health.ge.com')){
                            image_entry_array << counter
                        }
                        counter = counter + 1
                    }
                    println image_entry_array
                    image_entry_array.each{
                        def image_place = it
                        def tag_place = it + 1
                        def image_name = additional_values_file_lines.get(image_place).split(':',2)[-1].trim()
                        def image_tag = additional_values_file_lines.get(tag_place).split(':',2)[-1].trim()
                        println image_name
                        println image_tag
                        def image_complete_url = "${image_name}:${image_tag}"
                        //def image_name_tar = image_name.split('/')[-1] + '-' + image_tag + '.tar'
                        def image_name_tar = image_name.split('/')[-1] + '.tgz'
                        println image_name_tar
                        sh """
                            docker pull ${image_complete_url}
                            docker save ${image_complete_url}  | gzip > ${image_name_tar}
                            chmod +r ${image_name_tar}
                            mv ${image_name_tar} ${app_iso_location_docker}
                        """
                        sh "ls -lart ${app_iso_location_docker}"
                    }
                }
            }
        }
    }
    catch(err){
        println("PIPELINE_ERROR appISOPackageDocker : " + err.getMessage())
        throw err
    }
    println ("appISOPackageDocker [end]")
}
