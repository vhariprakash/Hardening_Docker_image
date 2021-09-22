def buildAndUnitTest(allParamDict, arch, contArgs, publish_repo, version, build_version, currentTimestamp)
{
    def reportDirectory_unitTest
    def build_and_publish = new org.utils.build_and_publish()
        build_and_unit_test_prestep = allParamDict["build_and_unit_test_prestep"] ?: ''
        build_and_unit_test_poststep = allParamDict["build_and_unit_test_poststep"] ?: ''
        println("build type:" + allParamDict['buildType'])
        if (allParamDict['buildType'] == 'maven') {
            build_and_publish.maven_build (arch, contArgs, build_and_unit_test_prestep, build_and_unit_test_poststep, allParamDict['mvnBuildDetails'], build_version, allParamDict['buildCommand'], allParamDict['branch_type'], allParamDict['git_branch_name'], currentTimestamp, allParamDict["defaultPomVersion"])
            if(allParamDict['unitTestReportDir']!= null){
                reportDirectory_unitTest = "${WORKSPACE}/${allParamDict['unitTestReportDir']}"
            }
        } else if (allParamDict['buildType'] == 'gradle') {
            println("gradle build")
            if(allParamDict['unitTestReportDir']!= null) {
                reportDirectory_unitTest = "${WORKSPACE}/.gradle/reports/test/"
            }
            build_and_publish.gradle_build(arch, contArgs, allParamDict['buildCommand'] ?: ' ', allParamDict["enableDebug"], build_and_unit_test_prestep, build_and_unit_test_poststep)
        } else if (allParamDict['buildType'] == 'go') {
            println("GO build")
            build_and_publish.go_build(arch, contArgs, allParamDict['buildCommand'] ?: ' ', env.WORKSPACE, build_and_unit_test_prestep, build_and_unit_test_poststep)
            if(allParamDict['unitTestReportDir']!= null) {
                reportDirectory_unitTest = "${WORKSPACE}/.gogradle/reports/test/"
            }
        } else if (allParamDict['buildType'] == 'python') {
            println("Python build")
            build_and_publish.python_build(arch, contArgs, env.WORKSPACE, build_and_unit_test_prestep, build_and_unit_test_poststep)
        } else if (allParamDict['buildType'] == 'cpp') {
            println("CPP build")
            build_and_publish.cpp_build(arch, contArgs, allParamDict['buildCommand'] ?: ' ', build_and_unit_test_prestep, build_and_unit_test_poststep)
            if(allParamDict['unitTestReportDir']!= null) {
                reportDirectory_unitTest = "${WORKSPACE}/.cpp/reports/test/"
            }
        } else if (allParamDict['buildType'] == 'npm') {
            println("NPM build")
            build_and_publish.npm_build(arch, contArgs, allParamDict['buildCommand'], build_and_unit_test_prestep, build_and_unit_test_poststep)
        } else if (allParamDict['buildType'] == 'shell') {
            println("shell build")
            build_and_publish.shell_build(arch, contArgs, allParamDict['buildCommand'] ?: ' ', build_and_unit_test_prestep, build_and_unit_test_poststep)
            if(allParamDict['unitTestReportDir']!= null) {
                reportDirectory_unitTest = "${WORKSPACE}/${allParamDict['unitTestReportDir']}"
            }
        } else {
            println("ERROR: Unknown build type: " + allParamDict['buildType'])
            throw new Exception("Unknown build type")
        }
        return reportDirectory_unitTest 
}



