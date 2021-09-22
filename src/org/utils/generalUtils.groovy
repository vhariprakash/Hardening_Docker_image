package org.utils;

//** ------------------------INDEPENDENT CAN BE REUSED FOR ANY CODE {Required to me moved to out} ===[BEGINS]==== ----------------------- **
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
//** ------------------------INDEPENDENT CAN BE REUSED FOR ANY CODE {Required to me moved to out} ===[ENDS]==== ----------------------- **
