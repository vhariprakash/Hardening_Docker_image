package org.utils;
import groovy.json.JsonSlurper;
//** ------------------------INDEPENDENT CAN BE REUSED FOR ANY CODE {Required to me moved to out} ===[BEGINS]==== ----------------------- **

/**
 Function Name : assignDictionaryToVars
 Description   : Gets the variable name from dictionary key and assigns dictionary value to it
 Arguments     :
                dictionaryMap - Title for the section to be displayed
 Returns       : Returns all the variables with there respective values
*/

def assignDictionaryToVars(dictionaryMap){
    try{
        dictionaryMap.each{
            def varname = "${it.key}"
            def varvalue = "${it.value}"
            dynamicVarCreation(varname, varvalue)
            println "New variable from dictMap is ${varname} and value assigned is " + "${varvalue}"
            return "${varname}"
        }
    }
    catch(err){
        println "Oops....... An uncaught exception occured"
        println "**********Opportunity to catch this exception with better Display Message************"
        throw err
    }
}

/**
 Function Name : dynamicVarCreation
 Description   : Helps create a variable dynamically from the string assigned to
 Arguments     :
                 varName - The name of the variable to be created dynamically
                 varValue - The value to be assigned to the dynamically created variable
 Returns       : Returns the variable with value
*/

def dynamicVarCreation(varName, varValue){
    //import groovy.lang.Binding;
    //import groovy.lang.GroovyShell;
    new GroovyShell(binding).evaluate("""${varName} = '${varValue}'""")
}

/**
 * [WIP]
 Function Name : dynamicVarCreation
 Description   : Helps create a variable dynamically from the string assigned to
 Arguments     :
                 varName - The name of the variable to be created dynamically
                 varValue - The value to be assigned to the dynamically created variable
 Returns       : Returns the variable with value
*/

def curlPUT(varName, varValue){
    //import groovy.lang.Binding;
    //import groovy.lang.GroovyShell;
    new GroovyShell(binding).evaluate("""${varName} = '${varValue}'""")
}

/**
 * Function Name: sshPassScp
 * Description: Helps scp multiple files at once in the provided target node
 * Arguments: targetNodeCredId - Jenkins credential ID to access this scp
              targetNodeIP - IP of the target node
              sshPassScpMap -['destination location': "source location" ] for e.g. ['devopsDsl': "${edgeTargetNodeRootDir}/${isoname}/", devSourceCode: "${edgeTargetNodeRootDir}/${isoname}/"]
              PORT - port number open for ssh into the target node
*/

def sshPassScp(targetNodeCredId,targetNodeIP,sshPassScpMap,PORT){
    def inputValidation = ['targetNodeCredId':targetNodeCredId, 'targetNodeIP':targetNodeIP,'sshPassScpMap':sshPassScpMap,'PORT':PORT]
 /*    inputValidation.each{ key, value ->
         if (!value?.trim()){
             sh"""
             echo "sshPassScp validation failed as the value of ${key} is ${it}"
             exit 1
             """
         }
     }
     */
     sshPassScpMap.findAll { key, value ->
        copyLineTemplate = "Copying file: " + key + " to location " + value
        println copyLineTemplate
        def filename = key
        def targetNodeScpPath = value
        println "filename " + filename
        println "targetNodeScpPath" + targetNodeScpPath
        withCredentials([usernamePassword(credentialsId: targetNodeCredId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        sh"""
         sshpass -p $PASSWORD scp -rCP ${PORT} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ${filename} ${USERNAME}@${targetNodeIP}:${targetNodeScpPath}
         if [ \$? -ne 0 ]; then
            exit 1
         fi
        """
        copiedLineTemplate = "Copied file: " + key + " to location " + value
        println copiedLineTemplate
        }
    }
 }


/**
 * Function Name: sshPassCommand
 * Description: Helps ssh multiple commands at once in the provided target node
 * Arguments: targetNodeCredId - Jenkins credential ID to access this ssh
              targetNodeIP - IP of the target node
              sshPassCommandArray - array of commands to be executed ["ls" ,"chmod +x filename"]
              PORT - port number open for ssh into the target node
*/
 def sshPassCommand(targetNodeCredId,targetNodeIP,sshPassCommandArray,PORT){
     def output
     def inputValidation = ['targetNodeCredId':targetNodeCredId, 'targetNodeIP':targetNodeIP,'sshPassCommandArray':sshPassCommandArray,'PORT':PORT]

 /*    inputValidation.each{ key, value ->
         println "key : " + key
         println value
         if (!value?.trim()){
             sh"""
             echo "sshPassCommand validation failed as the value of ${key} is ${it}"
             exit 1
             """
         }
     }
     */
     println "Commands2 :"
     println sshPassCommandArray
     sshPassCommandArray.each {
        commandLineTemplate = "Executing command: " + it
        println commandLineTemplate
        def targetNodeCommand = it
        withCredentials([usernamePassword(credentialsId: targetNodeCredId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            cmd = """sshpass -p ${PASSWORD} ssh -p ${PORT} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ${USERNAME}@${targetNodeIP} \"${targetNodeCommand}\" """
            output = sh(returnStdout: true, returnStatus: false, script: cmd )
        }
    }
    println "output of command: ${output}"
    println "sshPassCommand completed"
    return output
 }

/**
 * Function Name: checkDirExistAndMkdir
 * Description: Checks the directory exists or not before making new
 * Arguments: dirName: directory name to be created
*/
def checkDirExistAndMkdir(dirName) {
    def exists = fileExists "${dirName}"
    if (!exists){
        new File("${dirName}").mkdir()
    }
}


/**
 * Function Name: underscoreToCamelCaseList
 * Description: converts underscore names to camelCase
 * Arguments: underscoreList: list of strings to be converted to camelcase
 * e.g: def vals = ['', ' ', 'id', 'transaction_id' ,'created_date']; underscoreToCamelCaseList(vals)
*/
def underscoreToCamelCaseList(underscoreList){
    def newConvertedList = []
    underscoreList.each{ String colName ->
        def smallCaseName = colName.toLowerCase().replaceAll('-','_')
        def newName = generalGroovyUtlis.underscoreToCamelCase(smallCaseName)
        newConvertedList.add(newName)
    }
    println "return camelCase converted list" +  newConvertedList
    return newConvertedList
}
/**
 * Function Name: underscoreToCamelCase
 * Description: converts underscore name to camelCase
 * Arguments: underscoreList: list of strings to be converted to camelcase
 * e.g: def vals = 'transaction_id'; underscoreToCamelCase(vals)
*/
String underscoreToCamelCase(String underscore){
    if(!underscore || underscore.isAllWhitespace()){
        return ''
    }
    return underscore.replaceAll(/_\w/){ it[1].toUpperCase() }
}

@NonCPS
def getDictFromJson(jsonString) {
    def parser = new JsonSlurper()
    LinkedHashMap json = new LinkedHashMap(parser.parseText(jsonString))
//    def newMap = new LinkedHashMap(lazyValueMap)
    return (json)
}

def getMd5SumFromJson(jsonString) {
    def parser = new JsonSlurper()
    LinkedHashMap json = new LinkedHashMap(parser.parseText(jsonString))
    return (json["checksums"]["md5"])
}

def getSha256FromJson(jsonString) {
    def parser = new JsonSlurper()
    LinkedHashMap json = new LinkedHashMap(parser.parseText(jsonString))
    return (json["checksums"]["sha256"])
}

def getUriFromJson(jsonString) {
    def parser = new JsonSlurper()
    LinkedHashMap json = new LinkedHashMap(parser.parseText(jsonString))
    return (json["uri"])
}

//** ------------------------INDEPENDENT CAN BE REUSED FOR ANY CODE {Required to me moved to out} ===[ENDS]==== ----------------------- **
