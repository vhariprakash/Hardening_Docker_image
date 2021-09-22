package org.utils;
// Utility to perform operations on artifactory

/**
 Function Name : check_if_artifact_exists
 Description   : Check if an artifact exists in Artifactory 
 Arguments     : 
                 artifactNameWithUrl - Complete URL where the artifact is
                                       uploaded to
 Returns       : true if File exists
                 false if File doesn't exist
*/
def check_if_artifact_exists(artifactNameWithUrl) {
    def shellObj = new org.utils.shell()
    response_code = shellObj.shell_ret_output("""curl -s -o /dev/null -I -w "%{http_code}" ${artifactNameWithUrl}""")
    def message = "${artifactNameWithUrl} is found"
    def ret_code = true 

    // If Response code is not 200, then file is not present in artifactory
    if(!response_code.equals('200')) {
        message = "${artifactNameWithUrl} is not available in artifactory"
        ret_code = false
    }
    println("${message}")
    return(ret_code)
}

/**
 Function Name          : copy_artifact
 Description            : Uses Curl command to copy from source to destination
 Arguments:
   src_location         : Source location of artifact
   dst_location         : Destination location of artifact
 Returns                : true  if copy successful
                          false if copy has failed
 Limitations            : Token can be referenced from a config variable.
*/
def copy_artifact(src_location, dst_location) {
    withCredentials([string(credentialsId: 'ArtifactoryLoginToken', variable: 'ApiToken')]) {
        // Curl Command with Access API Key
        def shellObj = new org.utils.shell()
        def curl_cmd = """curl -k -H 'X-JFrog-Art-Api: ${ApiToken}' -T """
        def cmd_with_params = curl_cmd + src_location + " " + dst_location
        def ret_val = shellObj.shell_ret_exitVal(cmd_with_params)
        // Confirm that artifact is successfully copied
        return(check_if_artifact_exists(dst_location))
    }
}

/**
 Function Name          : download_artifact
 Description            : Uses Curl command to download from artifactory
 Arguments:
   src_location         : Artifactory location for the artifact
 Returns                : Nothing
*/
def download_artifact(artifact_location){
    withCredentials([string(credentialsId: 'ArtifactoryLoginToken', variable: 'ApiToken')]) {
        sh """ curl -O -H 'X-JFrog-Art-Api: ${ApiToken}' ${artifact_location}"""
    }
    sh """ls -ltr"""
}

/**
 Function Name          : ret_bld_increment_num
 Description            : Uses Artifactory aql to query for matching versioned RPMs and returns the latest Release found 
                          While this function is intended for use with RPM Pipelines ONLY, it may be used 
                          for any artifact type dependening on a running number
                          Also eliminates the need to maintain a Build Revision YAML File
 Arguments:
     rpm_name           : RPM Name with version
     repo_name          : Repo where the RPM resides
     token              : Access token for Artifactory being referred to
     artifactory        : Active Artifactory Location
 Returns                : Maximum value found + 1 OR 1 for possible new builds
 Limitation             : Can be replaced by a REST Client
 */
def ret_bld_increment_num(rpm_name, repo_name, artifactory, token = "ArtifactoryLoginToken", path) {
    def shellObj = new org.utils.shell()
    def output = ''
    def bld_increment = 0
    withCredentials([string(credentialsId: "${token}", variable: 'ApiToken')]) {
        // Compose Query for Artifactory AQL. Quotes are escaped, otherwise, the braces can fail the bash command
        // All Backslashes are to escape the double quotes and also the '$' sign in match
        def query = "--data 'items.find({\"repo\":\"${repo_name}\",\"name\":{\"\$match\":\"${rpm_name}\"}, \"path\":\"${path}\"})"
        def fields = ".include(\"name\", \"repo\", \"path\")\'"
        def curl_cmd = "curl -X POST -k -H 'Content-Type:text/plain' -H 'X-JFrog-Art-Api:${ApiToken}' '${artifactory}/api/search/aql' "
        curl_cmd = curl_cmd + query + fields
        // Output is in JSON Format, but since it seems plain text is supported, we'll convert it using readJSON
        output = readJSON text: shellObj.shell_ret_output(curl_cmd)
    }
    if(output['results'].size()==0){
        // Seems like first build of service, return 1
        bld_increment = 1
        println("First build. Returning ${bld_increment}")
    }
    else{ 
        // Otherwise, we need to calculate the running number by dissecting the RPM Name
        versions = []
        output['results'].each(){
            versions.add(it['name'].split('-')[-1].split("\\.")[0].toInteger())
        }
        // Increment it by one for current build
        bld_increment = versions.max() + 1
    }
    return bld_increment
}
