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
    try{
        debugMap.findAll { key, value ->
            debugLineTemplate = "Debug lines: " + key + " is " + value
            println debugLineTemplate
        }
    }
    catch(err){
        println "Oops....... An uncaught exception occured"
        println "**********Opportunity to catch this exception with better display message************"
        throw err
    }
}

/**
 Function Name : debugLines
 Description   : If enableDebugForDSL is marked true in Jenkinsfile,
                 then it will display all the vars with names listed
                 in provided array. Helpful for debugging purpose for devops implementation.
 Arguments     :
                 debugMap - Array which contains all the variables to be displayed
 Returns       : Debug lines statement with variable names and varaible values
*/
def debugLinesWithEnable(debugMap, enableDebugForDSL){
  try{
    if(enableDebugForDSL == 'true'){
        debugMap.findAll { key, value ->
            println "Debug lines: " + key + " is " + value
        }
    }
  }
  catch(err){
        println "Oops....... An uncaught exception occured"
        println "**********Opportunity to catch this exception with better display message************"
        throw err
  }
}

/**
 Function Name : sectionDisplay
 Description   : Helps organize different sections in Jenkinslog in a formatted way
 Arguments     :
                 title - Title for the section to be displayed
                 sectionHeadingFormat - h1, h2, h3, stage
 Returns       : Prints title in a formatted way
*/
def sectionDisplay(title,sectionHeadingFormat){
  int spaceNumber
  def quotingFormat
  try{
    int titleLength = title.length()
    if (sectionHeadingFormat == "h1"){
      spaceNumber = titleLength*5
      quotingFormat = "#"
    }
    else if(sectionHeadingFormat == "h2"){
      spaceNumber = titleLength*3
      quotingFormat = "*"
    }
    else if (sectionHeadingFormat == "h3"){
      spaceNumber = titleLength*2
      quotingFormat = "-"
    }
    else if (sectionHeadingFormat == "stage"){
      spaceNumber = titleLength*2
      quotingFormat = "-"
    }
    println quotingFormat.center(spaceNumber,quotingFormat)
    println title.toUpperCase().center(spaceNumber, " "+quotingFormat+" ")
    println quotingFormat.center(spaceNumber,quotingFormat)
  }
  catch(err){
    println "*************************************************************************************"
    println "**********Oops....... An uncaught exception occured**********************************"
    println "**********Opportunity to catch this exception with better display message************"
    println "*************************************************************************************"
    throw err
  }
}
