package org.utils;
/* Utility for GIT operations */


/**
 Function Name : checkout_git_repo
 Description   : checkout particular branch of git repo 
 Arguments     : 
   branch   - Branch to checkout 
   repoPath - Repo to checkout with Path 
*/
def checkout_git_repo(branch, repoPath) {
    echo "checking out ${branch} of ${repoPath}"
    git branch: "${branch}", changelog: false, credentialsId: 'root-user-docker06', poll: false, url: "git@gitlab-gxp.cloud.health.ge.com:${repoPath}.git"
}


/**
 Function Name : changed_files_in_last_commit
 Description   : List the files changed in the last commit
 Arguments     : 
   commitHash - Commit id of the HEAD  
    
*/
def changed_files_in_last_commit(commitHash) {
    def list_of_files_changes
    def num_of_parents_to_last_commit = sh(returnStdout: true, script: """ git rev-list --parents -n 1 ${commitHash} | wc -w """).trim().toInteger()
    if ( num_of_parents_to_last_commit > 2 ){
        println("This is a merge commit as the number of parents are more than 2")
        list_of_files_changes = sh(returnStdout: true, script: """ git log -m -1 --name-only --pretty="format:" ${commitHash} """)
    }
    else {
        println("This is a normal commit")
        list_of_files_changes = sh(returnStdout: true, script: """ git diff-tree --no-commit-id --name-only -r ${commitHash} """)
    }
    println(list_of_files_changes)
    return list_of_files_changes.trim()          
}

/**

 Function Name : check_commit_mesg_and_list_files
 Description   : Check commits with a specific message and List the files
 Arguments     : 
   dirPath - path of the directory containing the files inside the repo 
   filePattern - pattern of the files being searched for under the directory
   numOflogEntries - number of log entries to search for the specific commit mesg
   commitMesg - commit message to search for
   listFilesWithCommitMesg - boolean argument, true for listing files with matching commit mesg, false for listing files without matching commit mesg 
    
*/
def check_commit_mesg_and_list_files(dirPath,filePattern,numOflogEntries,commitMesg,listFilesWithCommitMesg) {
    def list_of_files = []
    def find_files = findFiles(glob: "${dirPath}/${filePattern}")
    for (counter = 0; counter < find_files.size(); counter++) {
        def search_for_files_with_same_commit_msg = sh(returnStdout: true, script: """ git log -${numOflogEntries} --oneline --grep=\"${commitMesg}\" -- ${find_files[counter].path} """).trim() 
        println(search_for_files_with_same_commit_msg)
        if (search_for_files_with_same_commit_msg.contains("${commitMesg}")) {
            if ("${listFilesWithCommitMesg}" == 'true') {
                println("${find_files[counter].name} has the commit message that is being searched for")  
                println("Adding files with the commit message to the list")
                list_of_files << "${find_files[counter].path}"
                }   
            }
        else {
            if ("${listFilesWithCommitMesg}" == 'false') {
                println("${find_files[counter].name} does not have the commit message that is being searched for")
                println("Adding files without the commit message to the list")
                list_of_files << "${find_files[counter].path}"
                }   
            }
        }
    println(list_of_files)
    return list_of_files          
}

/**
 Function Name : get_file_from_repo
 Description   : get a file from particular reference from
                 git repo
 Arguments     :
   branch   - Branch to checkout
   repoPath - Repo to checkout with Path
   filePath - Full Path of the file in repo
 Limitations   : 1 Currently using hardcoded GIT Token
                 Check and update to withCredentials
                 2 File is stored using redirection
                 Update if better method found
*/
def get_file_from_repo(branch, repoPath, filePath) {
    // First get file name from the file path
    def fileName = filePath.split('/')[-1]
    // Download file from GIT using GITLAB Api. Ideally we should download without redirection
    def curl_cmd = "curl --request GET --header 'PRIVATE-TOKEN: 4Khyhm-HAvxnzuaxyEtn' https://gitlab-gxp.cloud.health.ge.com/api/v4/projects/" + URLEncoder.encode("${repoPath}", 'UTF-8') + "/repository/files/" + URLEncoder.encode("${filePath}", 'UTF-8') + "/raw?ref=${branch} >> ${fileName}"
    // Shell Object to run curl commands
    def shellObj = new org.utils.shell()
    def output = shellObj.shell_ret_exitVal(curl_cmd)
    // Return FileName to use by the consumer of this function
    // This would prevent getting file name from file path in
    // consumer function again
    return fileName
}

/**
 Function Name : check_if_file_in_git
 Description   : check if a file exists on a git repo
 Arguments     :
   branch   - Branch to check for file
   repoPath - Repo where to check for file
   fileName - Filename to check for
*/
def check_if_file_in_git(branch, repoPath, fileName) {
   def gitConnection = new URL("https://gitlab-gxp.cloud.health.ge.com/api/v4/projects/" + URLEncoder.encode(repoPath, 'UTF-8') + "/repository/files/${fileName}?ref=${branch}").openConnection() as HttpURLConnection
    gitConnection.setRequestProperty('Authorization', "PRIVATE-TOKEN: 4Khyhm-HAvxnzuaxyEtn")
    def ret_val = (gitConnection.responseCode == 200)? true : false
    return ret_val 
}

/**
 Function Name : create_file_in_git
 Description   : create a file in git repo
                 on specified branch
 Arguments     :
   branch      - Branch to check for file
   repoPath    - Repo where to check for file
   filePath    - FilePath where file is to be created
   fileContent - file Content to write to the file
 Limitation    : 1 - The token used is a personally generated token
                 Better to add a Service token to Jenkins and use
                 withCredentials
                 2 - There could be a better way to compose the REST
                 API request
*/
def create_file_in_git(branch, repoPath, filePath, fileContent) {
    // use a Shell Object to call shell functions
    def shellObj = new org.utils.shell()
    def data = " --data \"branch=${branch}&content=${fileContent}&commit_message='Create New File'\" "
    def gitApiUrl =  " https://gitlab-gxp.cloud.health.ge.com/api/v4/projects/"
    def curl_cmd = "curl --request POST --header 'PRIVATE-TOKEN: 4Khyhm-HAvxnzuaxyEtn' "
    gitApiUrl = gitApiUrl + URLEncoder.encode(repoPath,'UTF-8') + "/repository/files/" + URLEncoder.encode(filePath,'UTF-8')
    curl_cmd = curl_cmd + data + gitApiUrl
    def ret_val = shellObj.shell_ret_exitVal(curl_cmd)
}

/**
 Function Name : update_file_in_git
 Description   : update a file in git repo
                 on specified branch
 Arguments
   branch      - Branch to check for file
   repoPath    - Repo where to check for file
   filePath    - FilePath where file is to be updated
   fileContent - file Content to write to the file
 Limitation    : 1 - The token used is a personally generated token
                 Better to add a Service token to Jenkins and use
                 withCredentials
                 2 - There could be a better way to compose the REST
                 API request
                 3 - Combine this function with create_file_in_git
                 passing requestMethod POST/PUT
*/
def update_file_in_git(branch, repoPath, filePath, fileContent) {
    // use a Shell Object to call shell functions
    def shellObj = new org.utils.shell()
    def data = " --data \"branch=${branch}&content=${fileContent}&commit_message='Create New File'\" "
    def gitApiUrl =  " https://gitlab-gxp.cloud.health.ge.com/api/v4/projects/"
    def curl_cmd = "curl --request PUT --header 'PRIVATE-TOKEN: 4Khyhm-HAvxnzuaxyEtn' "
    gitApiUrl = gitApiUrl + URLEncoder.encode(repoPath,'UTF-8') + "/repository/files/" + URLEncoder.encode(filePath,'UTF-8')
    curl_cmd = curl_cmd + data + gitApiUrl
    def ret_val = shellObj.shell_ret_exitVal(curl_cmd)
}

/**
 Function Name : create_tag_on_commit
 Description   : creates a tag in git repo
                 on specified commit
 Arguments     :
   commitId    - Commit ID to check for file
   repoUrl     - Repo URL for tagging
   tagName     - Tag to use for tagging operation
 Limitation    : The token used is a personally generated token
                 better to add a Service token to Jenkins and use
                 withCredentials
*/
def create_tag_on_commit(commitId, repoUrl, tagName) {
    println("Creating tag ${tagName} on ${commitId} for repo: ${repoUrl}")
    // use a Shell Object to call shell functions
    def shellObj = new org.utils.shell()
    // Get Repo path from complete SSH URL Path
    def repoPath = repoUrl.split(':')[-1].replace('.git','')
    // Tag Data
    def data = " --data \"tag_name=${tagName}&ref=${commitId}\" "
    def gitApiUrl =  " https://gitlab-gxp.cloud.health.ge.com/api/v4/projects/" + URLEncoder.encode("${repoPath}", 'UTF-8') + "/repository/tags"
    def curl_cmd = "curl --request POST --header 'PRIVATE-TOKEN: 4Khyhm-HAvxnzuaxyEtn' "
    curl_cmd = curl_cmd + data + gitApiUrl
    def tag_output = readJSON text:shellObj.shell_ret_output(curl_cmd)
    // Confirm if Tag Exists
    return(tag_output['name'].contains(tagName.trim()))
}


/**
 Function Name : check_if_tag_exists
 Description   : checks if tag exists in git repo
 Arguments     :
   repoUrl     - Repo URL for tagging
   tagName     - Tag to search
 Returns       :
               - true  if Tag exists
               - false if Tag absent
 Limitation    : The token used is a personally generated token
                 better to add a Service token to Jenkins and use
                 withCredentials
*/
def check_if_tag_exists(repoUrl, tagName) {
    println("Checking if Tag Exists")
    // use a Shell Object to call shell functions
    def shellObj = new org.utils.shell()
    // Get Repo path from complete SSH URL Path
    def repoPath = repoUrl.split(':')[-1].replace('.git','')
    // GITLab API URL Path
    def gitApiUrl =  " https://gitlab-gxp.cloud.health.ge.com/api/v4/projects/"
    // Curl Command to get tags
    def curl_cmd = "curl --request GET --header 'PRIVATE-TOKEN: 4Khyhm-HAvxnzuaxyEtn' ${gitApiUrl}" + URLEncoder.encode("${repoPath}", 'UTF-8') + "/repository/tags"
    def ret_val = shellObj.shell_ret_output(curl_cmd)
    def json_tags = readJSON text:ret_val
    // Return true/false depending on whether tag exists
    def tag_exists = json_tags['name'].contains(tagName.trim())
    println("Tag ${tagName} exists: ${tag_exists}")
    return tag_exists
}

/**
 Function Name : push_file_to_git
 Description   : Push a File to GIT
 Arguments     : eisVersion - Name of the Branch
                 filePath   - Name of the file with path
 Returns       : true  - if push successful
                 false - if push fails
*/
def push_file_to_git(branchName, filePath){
    // Object to call Shell functions
    def shellObj = new org.utils.shell()
    // Push manifest file to corresponding branch in GIT
    def add_message = shellObj.shell_ret_output("git add ${filePath}")
    def commit_message_manifest = shellObj.shell_ret_output("git commit --allow-empty -m  \'Updating ${filePath} \' ")
    def commit_hash_manifest = commit_message_manifest.split(' ')[1].split(']')[0]
    def reset_manifest = shellObj.shell_ret_output(" git reset --hard ")
    def pull_message_manifest = shellObj.shell_ret_output("git pull --rebase -s recursive -X ours origin ${branchName}")
    def push_message_manifest = shellObj.shell_ret_output("git push origin ${branchName}")
    def commit_hash_return_code_manifest = shellObj.shell_ret_output("""git log origin/${branchName} | grep -c \"commit ${commit_hash_manifest}\"""")
    def ret_val = true
    if (!commit_hash_return_code_manifest.toString().contains('1')){
        println("Commit id is not available in Git. Git Push Failed.")
        ret_val = false
    }
    echo 'Successfully Pushed to devops-deploy repo'
    return ret_val
}

