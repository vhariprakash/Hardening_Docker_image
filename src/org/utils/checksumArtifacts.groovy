
package org.utils;

/* Utility for md5 checksum operations */


/**
 Function Name : create_md5_file
 Description   : create the md5 file for a specific artifact 
 Arguments     : 
   md5_file_path_local   - Path on the local system where the md5 file resides 
   artifact_name- Artifact for which md5 checksum is required
   md5_file - md5 file name to be created
*/
/**
 Function Name : publish_md5_file
 Description   : publish the md5 file to artifctory for a specific artifact 
 Arguments     : 
    
   md5_location_in_artifactory- md5 file location in artifactory
   md5_file - md5 file name to be created
*/

/*def call(body) { - md5 file name to be created
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    echo 'Artifact Checksum'
    try{
	    retry(2) {
            def md5_file_path_local = "${config.artifactPath}"
            def md5_file = "${config.artifactName}.md5"
            def md5_location_in_artifactory = "${config.artifact_location}.md5"
            if("${config.create_md5_file}" == 'true') {
                create_md5_file("${md5_file_path_local}","${config.artifact_name}","${md5_file}")
            }
            if("${config.publish_md5_file}" == 'true') {
                publish_md5_file("${md5_file}","${md5_location_in_artifactory}")
            }
            

        }	  
    }  
    catch(err){
        echo 'Something went wrong:'+ err
        throw err
    }
} */

def create_md5_file(md5_file_path_local,artifact_name,md5_file) {
     def artifact_checksum = sh returnStdout: true, script: """md5sum ${md5_file_path_local}/${artifact_name}"""
                    println artifact_checksum
                    if (artifact_checksum.contains(' ')){
                        artifact_checksum = artifact_checksum.split(' ')[0]
                    }
                    writeFile file: md5_file, text: artifact_checksum
 }
 def publish_md5_file(md5_file,md5_location_in_artifactory) {
 
    withCredentials([string(credentialsId: 'ArtifactoryLoginToken', variable: 'ApiToken')]) {
                        sh """ 
                            unset https_proxy
                             curl --silent --show-error -H 'X-JFrog-Art-Api: ${ApiToken}' -T ${md5_file}  ${md5_location_in_artifactory}
                        """
                    }          
}
