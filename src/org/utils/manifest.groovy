package org.utils;
/* Utility to update ISO Utilities Manifest file in devops-deploy repo */

/**
 Function Name : update_utilities_manifest
 Description   : Update Artifact information under ISO Utilities in
                 ISO_Utilities_Manifest.yaml under devops-dsl repo 
 Arguments     : eisVersion          - Name of the Branch for devops-dsl repo
                 fileName            - Name of the Subcomponent under 
                                       ISO Utilities section
                 projectName         - Repo Name to which the Artifact 
                                       belongs to
                 artifactNameWithUrl - Complete URL where the artifact is 
                                       uploaded to
 Returns       : Nothing
 Limitations   : Doesn't work with other manifests and other Repos, should be 
                 rewritten
*/
def update_utilities_manifest(eisVersion, fileName, projectName, artifactNameWithUrl) {
    echo 'Utilities Manifest will be updated'
    // Artifact object to query Artifactory
    def artifObj = new org.utils.artifact()
    // Object to call git functions
    def scmObj = new org.utils.scm()
    // Object to call Shell functions
    def shellObj = new org.utils.shell()
    // Common variable to capture shell output
    def shellOutput = ''
    def raw_deploy_response_code = 0

    // If File doesn't exist in artifactory, then no need to proceed
    if(!artifObj.check_if_artifact_exists(artifactNameWithUrl)) {
        // Exit from Job
        shellObj.shell_ret_exitVal('exit 1')
    }

    // Checkout devops-deploy
    dir('devops-deploy'){
        scmObj.checkout_git_repo(eisVersion, 'Edison-Imaging-Service/devops-deploy')
        def utilities_manifest = [:]
        utilities_manifest = readYaml file: ('ISO_Utilities_Manifest.yaml')
        shellOutput = shellObj.shell_ret_output("cat ISO_Utilities_Manifest.yaml")
        echo "${shellOutput}"
        // Remove exisiting Manifest file, the contents of the file are 
        // preserved in the object above
        shellOutput = shellObj.shell_ret_output("rm -rf ISO_Utilities_Manifest.yaml")

        // Write the artifact information to the manifest file object
        if (!utilities_manifest['ISO_Utilities']["${projectName}"]){
                utilities_manifest['ISO_Utilities']["${projectName}"] = [:]
        }
        utilities_manifest['ISO_Utilities']["${projectName}"]["${fileName}"] = artifactNameWithUrl
        writeYaml file: 'ISO_Utilities_Manifest.yaml', data: utilities_manifest

        // Update ISO Utilities Manifest File and push to corresponding branch in GIT
        def add_message = shellObj.shell_ret_output("git add ISO_Utilities_Manifest.yaml")
        echo add_message
        def commit_message_manifest = shellObj.shell_ret_output("git commit --allow-empty -m  \'Updating ISO_Utilities_Manifest file \' ")
        def commit_hash_manifest = commit_message_manifest.split(' ')[1].split(']')[0]
        def reset_manifest = shellObj.shell_ret_output(" git reset --hard ")
        def pull_message_manifest = shellObj.shell_ret_output("git pull --rebase -s recursive -X ours origin ${eisVersion}")
        def push_message_manifest = shellObj.shell_ret_output("git push origin ${eisVersion}")
        echo push_message_manifest
        def commit_hash_return_code_manifest = shellObj.shell_ret_output("""git log origin/${eisVersion} | grep -c \"commit ${commit_hash_manifest}\"""")
        if (!commit_hash_return_code_manifest.toString().contains('1')){
            error("Commit id is not available in Git. Git Push Failed.")
        }
        else {
            echo 'Successfully Pushed to devops-deploy repo'
            echo commit_hash_return_code_manifest
        }
    }
}

/**
 Function Name : ret_yaml_obj
 Description   : Reads file content to a YAML object and returns it
                 for further reads
                 This can be used for any sort of YAML Files and allows
                 caller function to retrieve values using keys
 Arguments     : branch       - Master / Release Branch name
                 repoPath     - Repo where the Build revision file resides
                 filePath     - File Path of the YAML file
 Returns       : yaml_obj     - File content in a YAML Object
*/
def ret_yaml_obj(branch, repoPath, filePath){
    // Object to call git functions
    def scmObj = new org.utils.scm()
    // Object to call shell functions
    def shellObj = new org.utils.shell()

    // Download file from git and get Filename from file path
    def fileName = scmObj.get_file_from_repo(branch, repoPath, filePath)

    // If file exists, then, read data into yaml object
    def yaml_obj = readYaml file: fileName

    // Print File Content, so the user can refer to current YAML file
    println(yaml_obj) 

    // Return the YAML Object
    return yaml_obj
}


/**
 Function Name : write_yaml_obj_to_file
 Description   : Writes YAML Object to file and pushes it to GIT Repo
                 This can be used for any YAML File
 Arguments     : branch       - Master / Release Branch name
                 repoPath     - Repo where the Build revision file resides
                 yamlObj     - YAML Object to write to file
                 filePath     - File Path of the YAML file in GIT Repo
*/
def write_yaml_obj_to_file(branch, repoPath, yamlObj, filePath){
    // Object to call git functions
    def scmObj = new org.utils.scm()
    // Object to call shell functions
    def shellObj = new org.utils.shell()
    // Get Repo Dir
    println("RepoPath is ${repoPath}")
    def repoDir = repoPath.split('/')[-1]
    // Return Value for Pushing File
    def ret_val = true
    // Checkout GIT Repo and push Updated file
    dir(repoDir){
        // Checkout devops-deploy branch
        scmObj.checkout_git_repo(branch, repoPath)

        // Remove exisiting file, the contents of the file are
        // in the yamlObj argument
        shellOutput = shellObj.shell_ret_output("rm -rf ${filePath}")
        println("YAML DATA IS: ${yamlObj}")

        // Update the file with content from yaml_obj and push it to git
        writeYaml file: filePath, data: yamlObj

        // Print file to console as reference to user.
        shellOutput = shellObj.shell_ret_output("cat ${filePath}")

        // Push Revision file to GIT
        retry(3){
            ret_val = scmObj.push_file_to_git(branch, filePath)
            sleep(10)
        }
    }
    return ret_val
}

