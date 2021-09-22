def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    println("appISOPackageACA [start]")
    try{
        def group = "${config.group_name}"
        def iso_manifest_absolute_location = sh(returnStdout: true, script: """ find . -name ${group}.yaml """).trim()
        def app_iso_location_aca= "${config.appISOLocationACA}" + '/'
        def temp_location_aca = pwd() + '/temp_aca'
        def temp_untar_location_aca = pwd() + '/temp_aca_utar'
        String buildPromotedArtifactsIso = "${config.promotedArtifacts}"
        println buildPromotedArtifactsIso
        sh """
            rm -rf ${temp_location_aca}
            rm -rf ${temp_untar_location_aca}
            mkdir -p ${temp_location_aca}
            mkdir -p ${temp_untar_location_aca}
        """
        def iso_manifest_file_parse = (!iso_manifest_absolute_location?.trim()) ? error("ISO group name is null or empty"): readYaml (file:iso_manifest_absolute_location)

        sh "cat ${iso_manifest_absolute_location}"
        if (iso_manifest_file_parse.keySet()) {
            iso_manifest_file_parse.keySet().each {
                def aca_file_name = ''
                sh "rm -rf ${it}"
                def repo_to_clone = iso_manifest_file_parse[it]['repo_url']
                def isPromoted = iso_manifest_file_parse[it]['promoted']
                def additional_values_commit_hash
                additional_values_commit_hash = iso_manifest_file_parse[it]['commit_id_additional_values']
                if (buildPromotedArtifactsIso == 'true'){
                    if (isPromoted == 'yes'){
                        additional_values_commit_hash = iso_manifest_file_parse[it]['promotion-commitid-additional-values-yaml']
                    }
                    else{
                        throw new Exception("**********************${it} is not promoted. Failing the job**********************")
                    }
                }
                def manifest_file_path = iso_manifest_file_parse[it]['manifest_file_location']
                checkout ( [$class: 'GitSCM', branches: [[name: "${additional_values_commit_hash}" ]], userRemoteConfigs: [[
                    credentialsId: 'ssh-Jenkins-s02',
                    url: "${repo_to_clone}"
                ]]])
                echo 'break01'
                sh "cat ${manifest_file_path}"
                def manifest_file_path_parse = readYaml file: manifest_file_path
                if(manifest_file_path_parse['app']){
                    if(manifest_file_path_parse['app']['aca']){
                        if(manifest_file_path_parse['app']['aca']['filename']){
                            aca_file_name = manifest_file_path_parse['app']['aca']['filename']
                            println "aca_file_name: ${aca_file_name}"
                        }
                    }
                }

                def application_name = "${it}"
                dir(it){
                    echo 'DOWNLOAD ACA'
                    sh "rm -rf ${temp_location_aca}/*"
                    if(iso_manifest_file_parse[application_name]['aca']){
                        if(iso_manifest_file_parse[application_name]['aca'].keySet()){
                            iso_manifest_file_parse[application_name]['aca'].keySet().each{
                                def aca_app_name = it
                                if(iso_manifest_file_parse[application_name]['aca'][aca_app_name].keySet()){
                                    if (iso_manifest_file_parse[application_name]['aca'][aca_app_name]['artifact_path']){
                                        def aca_download_url = iso_manifest_file_parse[application_name]['aca'][aca_app_name]['artifact_path']
                                        sh"""
                                            unset https_proxy
                                            if wget --spider ${aca_download_url} 2>/dev/null;
                                                then
                                                    wget -P ${temp_location_aca} ${aca_download_url}

                                                else
                                                    echo 'Following ACA is not available in Source.'
                                                    echo ${aca_download_url}
                                                    exit 1 ;
                                            fi
                                        """
                                    }
                                }
                            }
                        }
                        def aca_list = sh returnStdout: true, script: """ls ${temp_location_aca}"""
                        def aca_file_array = []
                        aca_file_array = aca_list.split('\n')
                        def aca_file_array_size = aca_file_array.size()
                        echo 'break006'
                        println "aca_file_array_size is ${aca_file_array_size}"
                        if (aca_file_array_size > 1){
                            echo 'break007'
                            echo "There are multiple ACAs defined for ${application_name}, count: ${aca_file_array_size}"
                            aca_file_array.each{
                                sh"""
                                    tar -C ${temp_untar_location_aca} -xvf ${temp_location_aca}/${it}
                                """
                            }
                            def aca_tar_location = "${app_iso_location_aca}${aca_file_name}"
                            sh """
                                cd ${temp_untar_location_aca}; tar -zcvf ${aca_tar_location} .
                                ls ${app_iso_location_aca}
                            """

                        }else{
                            echo 'break007'
                            echo "Just One ACA defined for ${application_name}"
                            echo "aca_file_name is: ${aca_file_name}"
                            echo "app_iso_location_aca is ${app_iso_location_aca}"
                            sh """
                                mv ${temp_location_aca}/* ${app_iso_location_aca}${aca_file_name}
                                ls ${app_iso_location_aca}
                            """
                        }
                    }
                }
            }
        }
    }
    catch(err){
        println("PIPELINE_ERROR appISOPackageACA : " + err.getMessage())
        throw err
    }
    println("appISOPackageACA [end]")
}
