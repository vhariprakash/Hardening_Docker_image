def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    println ("appISOPackageManifest [end]")
    try{
        def group = "${config.group_name}"
        def iso_manifest_absolute_location = sh(returnStdout: true, script: """ find . -name ${group}.yaml """).trim()
        println "${iso_manifest_absolute_location}"
        //def iso_manifest_absolute_location = "iso-manifests/${group}.yaml"
        def app_iso_location_manifest= "${config.appISOLocationManifest}" + '/.'
        def iso_manifest_file_parse = (!iso_manifest_absolute_location?.trim()) ? error("ISO group name is null or empty"): readYaml (file:iso_manifest_absolute_location)
        println "iso_manifest_file_parse ${iso_manifest_file_parse}"
        if (iso_manifest_file_parse.keySet()) {
            iso_manifest_file_parse.keySet().each {
                sh "rm -rf ${it}"
                dir(it){
                    def repo_to_clone = iso_manifest_file_parse[it]['repo_url']
                    def additional_values_commit_hash = iso_manifest_file_parse[it]['commit_id_additional_values']
                    def manifest_relative_location = iso_manifest_file_parse[it]['helm_chart_location']
                    def manifest_file_path = iso_manifest_file_parse[it]['manifest_file_location']
                    checkout ( [$class: 'GitSCM', branches: [[name: "${additional_values_commit_hash}" ]], userRemoteConfigs: [[
                        credentialsId: 'ssh-Jenkins-s02',
                        url: "${repo_to_clone}"
                    ]]])
                    sh """
                        cp ${manifest_file_path} ${app_iso_location_manifest}
                    """
                }
            }
        }
    }
    catch(err){
        println("PIPELINE_ERROR appISOPackageManifest : " + err.getMessage())
        throw err
    }
    println ("appISOPackageManifest [end]")
}
