package org.utils;
/*Utility to check ISO manifest file has all the required entries


 Function Name : check_if_valid_manifest
 Description   : Check if the content of a dictionary is as expected
 Arguments     : isoManifestData - a dictionary containing the yaml file contents
 Returns       : false if data is not as expected
                true if data has following attributes-
                    repo_url
                    commit_id
                    commit_id_additional_values
                    helm_chart_location
                    manifest_file_location
                    promoted

*/



def check_if_valid_manifest(isoManifestData){
    def ret_code = true
    def message = "Not a valid iso manifest File"
    if (isoManifestData.keySet()){
        isoManifestData.keySet().each {
            if ((!isoManifestData[it]['repo_url'])||(!isoManifestData[it]['commit_id'])||(!isoManifestData[it]['commit_id_additional_values'])||(!isoManifestData[it]['helm_chart_location'])||(!isoManifestData[it]['manifest_file_location'])||(!isoManifestData[it]['promoted'])){
                ret_code = false
                error("The build failed because ISO manifest file, ${it} is not correct.")
            }
        }
    }
    return(ret_code)
}
