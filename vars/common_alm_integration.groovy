import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
scmObj = new org.utils.scm()
def executeAlmIntegration(allParamDict) {
    println("stageIntegAlm [start]")
    def list_of_changed_feature_files = []
    if ((allParamDict["enableEtymo"] == 'true') && (allParamDict["sourceBranch"] == 'master') || (allParamDict["sourceBranch"] ==~ allParamDict["releaseBranch"])) {
        //START: Code to check if there is a change in feature file
        list_of_changed_feature_files = scmObj.check_commit_mesg_and_list_files(allParamDict["bddFeatureFilesLocation"],"*.feature",'1',"ci-skip",'false')
        println(list_of_changed_feature_files)
        String feature_update_flag
        if ((list_of_changed_feature_files == null) || (list_of_changed_feature_files.size() == 0)) {
            feature_update_flag = 'false'
        } else {
            feature_update_flag = 'true'
        }
        if (allParamDict["enableDebugForDSL"] == 'true') {
            println "Debug Lines: feature_update_flag " + "${feature_update_flag}"
        }
        if (feature_update_flag == 'true') {
            echo 'Etymo steps should get executed now'
        } else {
            echo "Etymo steps won't be executed since there is no change in feature file"
        }
        etymo {
            feature_file_loc = allParamDict["bddFeatureFilesLocation"].toString().trim()
            test_head = allParamDict["testHead"].toString().trim()
            request_head = allParamDict["requestHead"].toString().trim()
            etymo_extra_parameters = allParamDict["etymoParameters"]
            commitMessage = list_of_changed_feature_files
        }
        //END: Code to check if there is a change in feature file
    } else {
        Utils.markStageSkippedForConditional('ALM Integration')
    }
    println("stageIntegAlm [end]")
}