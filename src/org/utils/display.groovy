package org.utils;
// Utility for logs display related things

/**
 Function Name : debugLines
 Description   : If enableDebugForDSL is marked true in Jenkinsfile,
                 then it will display all the vars with names listed
                 in provided array. Helpful for debugging purpose for devops implementation.
 Arguments     :
                 debugMap - Array which contains all the variables to be displayed
 Returns       : Debug lines statement with variable names and varaible values
*/
def debugLines(debugMap){
    debugMap.findAll { key, value ->
        debugLineTemplate = "Debug lines: " + key + " is " + value
        println debugLineTemplate
        }
}

/**
 Function Name : sectionDisplay
 Description   : Helps organize different sections in Jenkinslog in a formatted way
 Arguments     :
                 title - Title for the section to be displayed
 Returns       : Prints title in a formatted way
*/
def sectionDisplay(title){
  println "####################################################################################"
  println "-------------- " + title + " -------------------"
  println "####################################################################################"
}
