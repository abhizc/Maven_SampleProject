#!groovy

// Sonar
def sonarEnv = 'Sonar PROD'
// Decide whether or not to publish SNAPSHOTs
def publishSnap = true
// Properties secrets
def propsWithSecrets = ['external.properties']
// ----------------------- You should not need to change below ----------------------- //
// Properties src path
def propsSrcPath = 'config'
// propsFullTgtPath = propsBaseTgtPath + "/" + propsAppPath ?: depArtifactId
def propsAppPath = ''
// Plans src path
def plansPath = 'PLANS'
// deployment package in case we are in multiproject
def depArtifactId = ''
// deployment package type: ear or war
def depArtifactPac = ''
// test package
def depTestArtifact = ''
// test package type: jar or rar
def depTestArtifactPac = ''
// WebLogic target in case it is differs from the artifactID
def wls_target = ''
def wls_name = ''
// Nexus IQ application ID - if empty, defaults to artifactID
def iqApplication = 'pipelinespoc__sam'
// mvn ${mvn_build_publish_param}
def mvn_build_publish_param = 'clean package deploy -DskipTests=true'
// mvn ${mvn_build_package_param}
def mvn_build_package_param = 'clean package -DskipTests=true'
// mvn ${mvn_build_test_param}
def mvn_build_tests_param = 'clean test-compile'
// mvn ${mvn_build_sonar_param}
def mvn_build_sonar_param = 'sonar:sonar'
// Current environments pattern
def envPat = /ALL|DEV|TST|PRD/
// ----------------------------- End of: # Change here # ----------------------------- //
// ----------------------------------------------------------------------------------- //

// ----------------------------------------------------------------------------------- //
// ------------------------------ DO NOT CHANGE BELOW!!! ----------------------------- //
// ----------------------------------------------------------------------------------- //
// Please do not change below unless strictly necessary. In case you do so,
// keep the same structure and stages, and inform:
// Jhonny.Oliveira@ema.europa.eu and/or 3LDEVOPS

// path to place the properties files on the target servers. The application name, as in artifactId, is automatically appended to this path
def propsBaseTgtPath = '/temp/'
// agent to be used
def agent_dev = 'CT_SLAVE_DEV'
def agent_tst = 'CT_SLAVE_TEST'
def agent_prd = 'CT_SLAVE_PROD'
def agent_ui = 'UI_TEST_HUB'

// -------------------------------- Deploy properties -------------------------------- //
def runDeployProperties(ENV, SRCPATH, TGPATH) {
    unstash 'properties'
    //  These properties require WebLogic to be properly configured beforehand
    //   Settings for <application>_<1|2> (server 1/2) → Configuration → Server Start → Class Path:
    //    Set to the appropriate path on each node: /fs/ema/<artifactId>/node<1|2>/:$CLASSPATH
    srcDirExist = !sh(script: "test -d ${SRCPATH}/${ENV}", returnStatus: true)
    println "Deploying properties files!"
    if (srcDirExist) {

        def remote = [:]

        remote.name = "serveradmin"
        remote.host = "uv1708.emea.eu.int"
        remote.allowAnyHosts = true
        remote.failOnError = true
        withCredentials([d394389a-5899-45f4-898e-0b18d1ecd24b]) {
            remote.user = username
            remote.password = password
        }
        sshPut remote: remote, from: SRCPATH, into: TGPATH
    } else {
        println "Doesnot exist"
    }
}

def applySecrets(SECRETS, TGPATH) {
    for (secret in SECRETS) {
        mFiles = sh(script: "find ${TGPATH} -name '${secret}'", returnStdout: true).trim().split('\n')
        for (mFile in mFiles) {
            // below you need an extra "\" before the "\1"
            credIDs = sh(script: "sed -nr 's/.*#([0-9a-z]{8}-([0-9a-z]{4}-){3}[0-9a-z]{12}).(user|pwd)#.*/\\1/p' ${mFile} | sort | uniq", returnStdout: true).trim().split('\n')
            for (credID in credIDs) {
                withCredentials([usernamePassword(credentialsId: "${credID}", usernameVariable: 'WL_USER', passwordVariable: 'WL_PASS')]) {
                    sh "sed -i 's/#${credID}.user#/${WL_USER}/g' ${mFile}"
                    sh "sed -i 's/#${credID}.pwd#/${WL_PASS}/g'  ${mFile}"
                }
            }
        }
    }
}

// ---------------------------- Discover WebLogic servers ---------------------------- //
def discoverWLSservers(CREDID, URL, ARTIFACTID) {
    tmpURL = URL.replace('t3://', 'http://')
    println "Discovering Weblogic nodes!"
    servers = []
    withCredentials([usernamePassword(credentialsId: "$CREDID", usernameVariable: 'WL_USER', passwordVariable: 'WL_PASS')]) {
        cluNodes = sh(script: """#!/bin/bash
    		curl -s -H Accept:application/xml -X GET ${tmpURL}/management/tenant-monitoring/clusters/${ARTIFACTID} --user '${WL_USER}':'${WL_PASS}' | xmllint --shell <(cat) <<<'xpath //property[@name=\"servers\"]/array/object/property[@name=\"name\"]/value[@type=\"string\"]/text()' | grep 'content=' | sed 's/.*=//'
    		""", returnStdout: true).trim().split('\n')

        for (cluNode in cluNodes) {
            servers += sh(script: """#!/bin/bash
				curl -s -H Accept:application/xml -X GET ${tmpURL}/management/tenant-monitoring/servers/${cluNode} --user '${WL_USER}':'${WL_PASS}' | xmllint --shell <(cat) <<<'xpath //property[@name=\"currentMachine\"]/value[@type=\"string\"]/text()' | grep 'content=' | sed 's/.*=//'
				""", returnStdout: true).trim()
        }
    }
    return servers
}

// ------------------------------- Deploy to WebLogic -------------------------------- //
def runDeploy2W(CREDID, URL, GROUPID, ARTIFACTID, VERSION, PACKAGING, DEPLOYFROMNEXUS, TARGETS, NAME, ISMULTIPROJ, FINALNAME, PLAN = '') {
    unstash 'pom'
    // Retrieve required artifacts
    if (DEPLOYFROMNEXUS) {
        // Get artifact from Nexus
        println "Retrieving artifact from Nexus!"
        sh "mvn dependency:get -DgroupId=${GROUPID} -DartifactId=${ARTIFACTID} -Dpackaging=${PACKAGING} -Dversion=${VERSION} -Ddest=target/${FINALNAME}.${PACKAGING}"
    } else {
        unstash 'artifacts'
        if (ISMULTIPROJ) {
            sh "rm -Rf target && mkdir target && cd target && ln -s {../${ARTIFACTID}/target/,}${FINALNAME}.${PACKAGING} && cd .."
        }
    }

    println "Deploying to Weblogic!"
    withCredentials([usernamePassword(credentialsId: "$CREDID", usernameVariable: 'WL_USER', passwordVariable: 'WL_PASS')]) {
        // Attempt to un-deploy application
        try {
            // First, undeploy the application
            sh "mvn -nsu -Pdeployment weblogic:undeploy -Dverbose=true -Dfailonerror=false -Duser='${WL_USER}' -Dpassword='${WL_PASS}' -Dname=${NAME} -Dupload=true -Dtargets=${TARGETS} -Dadminurl='${URL}'"
        } catch (error) {
            println "Error during undeploy: " + error
        }

        // In case we are using plans
        xtraParams = PLAN ? '-Dplan=' + PLAN : PLAN
        // Deploy to WebLogic
        sh "mvn -nsu -Pdeployment weblogic:redeploy ${xtraParams} -Dverbose=true -DskipTests=true -Dfailonerror=true -Duser='${WL_USER}' -Dpassword='${WL_PASS}' -Dname=${NAME} -Dupload=true -Dsource=target/${FINALNAME}.${PACKAGING} -Dtargets=${TARGETS} -Dadminurl='${URL}'"
    }
}

// -------------------------- Deploy to DataBase - FlyWay ---------------------------- //
def runDeploy2DB(CREDID, URL, LOCATION = '') {
    // Retrieve required artifacts
    unstash 'pom'
    unstash 'db'

    // If location is defined
    myParm = LOCATION ? "-Dflyway.locations='${LOCATION}'" : ""

    // Deploy DataBase changes
    println "Applying DB changes with Flyway!"
    withCredentials([usernamePassword(credentialsId: "$CREDID", usernameVariable: 'WL_USER', passwordVariable: 'WL_PASS')]) {
        sh "mvn -Pdeployment -DskipTests flyway:migrate -Dflyway.url='${URL}' -Dflyway.user='${WL_USER}' -Dflyway.password='${WL_PASS}' ${myParm}"
    }
}

// ----------------------------------- Run tests ------------------------------------- //
def runTests(URL, GROUPID, ARTIFACTID, VERSION, PACKAGING, DEPLOYFROMNEXUS) {
    //node(myAGENT as String){
    // Retrieve required artifacts
    unstash 'pom'

//	println "Retrieving artifact from Nexus!"
//	if(DEPLOYFROMNEXUS){
    // Get artifact from Nexus
//		sh "mvn dependency:get -DgroupId=${GROUPID} -DartifactId=${ARTIFACTID} -Dpackaging=${PACKAGING} -Dversion=${VERSION} -Ddest=target/${ARTIFACTID}_tests-${VERSION}.${PACKAGING}"
//	} else{
    unstash 'selenium'
//	}

    println "Executing Selenium tests against: ${URL}!"
    // Run selenium - VERY IMPORTANT - quote are not support on the selenium arguments
    //bat "mvn compiler:testCompile failsafe:integration-test failsafe:verify -DchromeDriver=C:/devtools/selenium/chromedriver.exe -DhttpUrl=${URL}"
    bat "mvn compiler:testCompile failsafe:integration-test failsafe:verify -DhttpUrl=${URL}"
}

// ----------------------------- Unit tests parameters ------------------------------- //
//unitTestsDBparams([[ type, file, match, replac], [ type, file, matches, userCred ] ])
def unitTestsDBparams(mPARAMS) {
    for (mPARAM in mPARAMS) {
        pTYPE = mPARAM[0]
        pFILE = mPARAM[1]
        pMATCH = mPARAM[2]
        pREPLAC = mPARAM[3]

        switch (pTYPE) {
            case 'string':
                sh "sed -i 's/^\\(${pMATCH}\\).*/\\1${pREPLAC}/g' ${pFILE}"
                break
            case 'userCred':
                usrStr = pMATCH[0]
                pwdStr = pMATCH[1]
                withCredentials([usernamePassword(credentialsId: "${pREPLAC}", usernameVariable: 'WL_USER', passwordVariable: 'WL_PASS')]) {
                    sh "sed -i 's/^\\(${usrStr}\\).*/\\1${WL_USER}/g' ${pFILE}"
                    sh "sed -i 's/^\\(${pwdStr}\\).*/\\1${WL_PASS}/g' ${pFILE}"
                }
                break
            default:
                println "unitTestsDBparams: Don't know what to do"
                break
        }
    }
}

// Global variables, do not change
boolean isRelease
def isMaster
def isPublished = false
boolean dbMigExist = true
boolean propsExist = true
boolean plansExist = true
boolean selenExist = true
def tgEnviro = 'ALL'
def tgProfile
def uiGitTag
boolean isMultiProj = false
def finalName
def propsFullTgtPath
def planPath = ''

pipeline {
    agent { label "${agent_dev}" }
    options {
        buildDiscarder(logRotator(numToKeepStr: '3'))
        timestamps()
        skipDefaultCheckout()
    }

    parameters {
        booleanParam(name: "isRelease", description: "Release", defaultValue: false)
        string(name: 'releaseVersion', defaultValue: 'X.X.X.X', description: 'Fill in your release version')
        string(name: 'developmentVersion', defaultValue: 'X.X.X.X-SNAPSHOT', description: 'Fill in next development version?')
        string(name: 'tgEnviro', defaultValue: 'ALL', description: 'Choose from the following: ALL, DEV, TST, PRD')
        string(name: 'uiGitTag', defaultValue: '', description: 'Enter the tag you would like to deploy: e.g: 1.5.2.1')
        string(name: 'tgProfile', defaultValue: '', description: 'Enter the Profile name to be run')
    }

    stages {
        stage('Setup') {
            steps {
                checkout scm
                script {
                    // Get parametrized secrets from the UI
                    wls_cred[0] = env.wls_cred_dev ?: wls_cred[0]
                    wls_cred[1] = env.wls_cred_tst ?: wls_cred[1]
                    wls_cred[2] = env.wls_cred_prd ?: wls_cred[2]
                    tgEnviro = env.tgEnviro ?: tgEnviro
                    uiGitTag = env.gitTag

                    // Validate the environment belong to the expectable list of environments
                    if (tgEnviro ==~ envPat) {
                        println "tgEnviro: ${tgEnviro}"
                    } else {
                        currentBuild.result = 'ABORTED'
                        error('The target environment (${tgEnviro}) is not within the expectable patern: ${envPat}!')
                    }

                    // Validate the entered tag matchs EMA standard patern and checkout
                    if (uiGitTag && !(uiGitTag ==~ /((\d+)\.){3}(\d+)/)) {
                        currentBuild.result = 'ABORTED'
                        error('The git tag (${uiGitTag}) is not within the expectable patern: d.d.d.d!')
                    } else if (uiGitTag) {
                        println "Retrieving git TAG: ${uiGitTag}"
                        sh "git checkout tags/${uiGitTag}"
                    } else {
                        println "Using latest git commit!"
                    }

                    // We need this because "pipeline-utility-steps" is not installed and readMavenPom() recomends doing it the way we do it below
                    mvnVersion = sh(script: "mvn -B -q -DforceStdout help:evaluate -Dexpression=project.version", returnStdout: true).trim()
                    mvnArtifactId = sh(script: "mvn -B -q -DforceStdout help:evaluate -Dexpression=project.artifactId", returnStdout: true).trim()
                    mvnGroupId = sh(script: "mvn -B -q -DforceStdout help:evaluate -Dexpression=project.groupId", returnStdout: true).trim()
                    mvnPackaging = sh(script: "mvn -B -q -DforceStdout help:evaluate -Dexpression=project.packaging", returnStdout: true).trim()
                    if (mvnPackaging == 'pom') {
                        isMultiProj = true
                        //mvnModules    = sh( script: "mvn -Dexec.executable='echo' -Dexec.args='\${project.artifactId},\${project.packaging}' exec:exec -q -DforceStdout", returnStdout: true).trim().split('\n')
                        mvnModules = sh(script: "mvn -B -q -DforceStdout help:evaluate -Dexpression=project.modules | sed -n 's/.*<string>\\(.*\\)<\\/string>.*/\\1/p'", returnStdout: true).trim().split('\n')
                        if (!depArtifactId || !depArtifactPac || !depTestArtifact || !depTestArtifactPac) {
                            println 'POM packaging detected, this is a multi-module project'
                            for (mvnModule in mvnModules) {
                                line = sh(script: "cd ${mvnModule} && mvn -B -q -DforceStdout help:evaluate -Dexpression=project.packaging", returnStdout: true).trim()
                                if (!depArtifactId) {
                                    if (line ==~ /war|ear/) {
                                        depArtifactId = mvnModule
                                        depArtifactPac = line
                                        continue
                                    }
                                }
                                if (!depTestArtifact) {
                                    if (mvnModule ==~ /.*_tests/) {
                                        depTestArtifact = mvnModule
                                        depTestArtifactPac = line
                                    }
                                }
                            }

                            if (!depArtifactId) {
                                currentBuild.result = 'ABORTED'
                                error('This is a Maven Multi Project, however, we were unable to figure out the deployment artifact!')
                            }
                        }
                        finalName = sh(script: "cd ${depArtifactId} && mvn -B -q -DforceStdout help:evaluate -Dexpression=project.build.finalName | sed 's/null object or invalid expression//' && cd ..", returnStdout: true).trim()
                    } else {
                        depArtifactId = depArtifactId ?: mvnArtifactId
                        depArtifactPac = depArtifactPac ?: mvnPackaging
                        finalName = sh(script: "mvn -B -q -DforceStdout help:evaluate -Dexpression=project.build.finalName | sed 's/null object or invalid expression//'", returnStdout: true).trim()
                    }

                    println "depArtifact:${depArtifactId}, depArtifactPac:${depArtifactPac}, depTestArtifact:${depTestArtifact}, depTestArtifactPac:${depTestArtifactPac}, finalName:${finalName}!"

                    gitHead = sh(script: 'git show -s --pretty=%d HEAD', returnStdout: true).trim()

                    // Debug
                    println "gitHead:${gitHead}"

                    
                    // Detect if it is a release
                    isRelease = mvnVersion ==~ /^\d+\.\d+\.\d+\.\d+$/
                    // Detect master branch
                    isMaster = gitHead ==~ /.*\/master\)$/
                    verMatch = gitHead ==~ /^.*tag:\s*${mvnVersion},?.*\)$/
                    println "mvnGroupId:${mvnGroupId}, artifactId:${depArtifactId}, Version:${mvnVersion}, depArtifactPac:${depArtifactPac}, RELEASE:${isRelease}, isMaster:${isMaster}, GitAndMvnMatch:${verMatch}!"

                    // Crosscheck if the project breaks the release convention
                    if (isRelease && !((isMaster || uiGitTag) && verMatch)) {
                        currentBuild.result = 'ABORTED'
                        error('The releases are only authorized in the master branch (unless deploying from Tag) and if both Git and Maven versions match!')
                    }

                    // Check if package already exists in the repository
                    if (isRelease || publishSnap) {
                        // Check if artifact exists in Nexus
                        remArtifact = sh(script: "mvn -B org.honton.chas:exists-maven-plugin:0.1.0:remote -Dexists.skipIfSnapshot=false | grep -A1 '@.*${depArtifactId}'", returnStdout: true).trim().split('\n')
                        isPublished = remArtifact ==~ /.*setting maven.deploy.skip=true.*/
                        println "isPublished: ${isPublished}"
                    }

                    try {
                        stash includes: "**/${propsSrcPath}/**", name: 'properties'
                    } catch (Exception e) {
                        println "No property files found - ${e}!"
                        propsExist = false
                    }

                    // Define full path to deploy properties from previous parameters
                    propsFullTgtPath = propsBaseTgtPath + "/" + (propsAppPath ?: depArtifactId)
                   
                  dbMigExist = false              
                  plansExist = false
                  selenExist = false
                  
                    println "--- dbMigExist:${dbMigExist},propsExist:${propsExist},plansExist:${plansExist},selenExist:${selenExist} ---"
                }
            }
            post {
                success {
                    stash includes: '**/pom.xml', name: 'pom'
                }
            }
        }
        stage('Build') {
            when { expression { tgEnviro == 'ALL' || tgEnviro == 'DEV' || !isPublished } }
            steps {
                script {

                    // Start the build
                    if (isRelease || publishSnap) {
                        // Build and upload the package to the repo
                        sh "mvn ${mvn_build_publish_param}"
                    } else {
                        sh "mvn ${mvn_build_package_param}"

                        // to use when we don't want to publish
                        stash includes: "**/target/*.ear,**/target/*.war,**/target/*.jar,**/target/*.rar", name: 'artifacts'
                    }
                }
            }
            post {
                success {
                    // save report
                    junit skipPublishingChecks: true, allowEmptyResults: true, testResults: '**/surefire-reports/*.xml'
                    // run Nexus Lifecycle policy evaluation
                    // save artifacts
                    archiveArtifacts artifacts: "**/target/*.ear,**/target/*.war,**/target/*.jar,**/target/*.rar"
                    cleanWs()
                }
            }
        }
        // ---------------------------- Deploy to DEV ---------------------------- //
        stage('Deploy to DEV') {
            agent { label "${agent_dev}" }
            when { expression { tgEnviro == 'ALL' || tgEnviro == 'DEV' } }
            steps {
                script {

                    // Copy properties' files and apply secrets
                    if (propsExist) {
                        // Copy preperties
                        runDeployProperties('dev', propsSrcPath, propsFullTgtPath)
                    }
               }
            }
            post {
                success { cleanWs() }
            }
        }
        stage('Tests in DEV') {
            agent { label "${agent_ui}" }
            when { expression { selenExist && (tgEnviro == 'ALL' || tgEnviro == 'DEV') } }
            steps {
                runTests(app_url[0], mvnGroupId, depArtifactId, mvnVersion, depArtifactPac, (isRelease || publishSnap))
            }
            post {
                always { junit allowEmptyResults: true, testResults: '**/failsafe-reports/*.xml' }
                success { cleanWs() }
            }
        }
        // ---------------------------- Deploy to TST ---------------------------- //
        stage('Approval to test') {
            when { expression { isRelease && (isMaster || uiGitTag) && (tgEnviro == 'ALL' || tgEnviro == 'TST') } }
            steps {
                timeout(time: 24, unit: "HOURS") {
                    input message: "Did you update the Test environment data with a fresh dataset?", submitter: "jenkins_administrators,jenkins_testers", ok: "Yes, proceed!"
                    input message: "Do you want to deploy to the Test environment?", submitter: "jenkins_administrators,jenkins_testers", ok: "Approve!"
                }
            }
        }
        stage('Deploy to TST') {
            agent { label "${agent_tst}" }
            when {
                expression { isRelease && (isMaster || uiGitTag) && (tgEnviro == 'ALL' || tgEnviro == 'TST') }
            }
            steps {
                script {
                    // Apply DB changes
                    if (dbMigExist) {
                        runDeploy2DB(db_cred[1], db_url[1])
                    }
                    // Copy properties' files and apply secrets
                    if (propsExist) {
                        // Copy preperties
                        runDeployProperties('tst', propsSrcPath, propsFullTgtPath)
                        // Applying secrets
                        applySecrets(propsWithSecrets, propsFullTgtPath)
                    }
                    // Apply secrets to plan
                    if (plansExist) {
                        unstash 'plans'
                        // Applying secrets
                        applySecrets(['plan.xml'], plansPath + '/tst')
                    }

                    // Deploy application to WebLogic
                    runDeploy2W(wls_cred[1], wls_url[1], mvnGroupId, depArtifactId, mvnVersion, depArtifactPac, (isRelease || publishSnap), (wls_target ?: depArtifactId), (wls_name ?: depArtifactId), isMultiProj, finalName, (plansExist ? plansPath + '/tst/plan.xml' : ''))
                }
            }
            post {
                success { cleanWs() }
            }
        }
        stage('Tests in TST') {
            agent { label "${agent_ui}" }
            when { expression { isRelease && (isMaster || uiGitTag) && selenExist && (tgEnviro == 'ALL' || tgEnviro == 'TST') } }
            steps {
                runTests(app_url[1], mvnGroupId, depArtifactId, mvnVersion, depArtifactPac, true)
            }
            post {
                always { junit '**/failsafe-reports/*.xml' }
                success { cleanWs() }
            }
        }
        // ---------------------------- Deploy to PRD ---------------------------- //
        stage('Approval to prod') {
            when { expression { isRelease && (isMaster || uiGitTag) && (tgEnviro == 'ALL' || tgEnviro == 'PRD') } }
            steps {
                timeout(time: 24, unit: "HOURS") {
                    input message: "Do you want to deploy to the PROD environment?", submitter: "jenkins_administrators,jenkins_testers", ok: "Approve!"
                }
            }
        }
        stage('Deploy to PRD') {
            agent { label "${agent_prd}" }
            when { expression { isRelease && (isMaster || uiGitTag) && (tgEnviro == 'ALL' || tgEnviro == 'PRD') } }
            steps {
                script {
                    // Apply DB changes
                    if (dbMigExist) {
                        runDeploy2DB(db_cred[2], db_url[2])
                    }
                    // Copy properties' files and apply secrets
                    if (propsExist) {
                        // Copy preperties
                        runDeployProperties('prd', propsSrcPath, propsFullTgtPath)
                        // Applying secrets
                        applySecrets(propsWithSecrets, propsFullTgtPath)
                    }
                    // Apply secrets to plan
                    if (plansExist) {
                        unstash 'plans'
                        // Applying secrets
                        applySecrets(['plan.xml'], plansPath + '/prd')
                    }

                    // Deploy application to WebLogic
                    runDeploy2W(wls_cred[2], wls_url[2], mvnGroupId, depArtifactId, mvnVersion, depArtifactPac, (isRelease || publishSnap), (wls_target ?: depArtifactId), (wls_name ?: depArtifactId), isMultiProj, finalName, (plansExist ? plansPath + '/prd/plan.xml' : ''))
                }
            }
            post {
                success { cleanWs() }
            }
        }
        stage('Tests in PRD') {
            agent { label "${agent_ui}" }
            when { expression { isRelease && (isMaster || uiGitTag) && selenExist && (tgEnviro == 'ALL' || tgEnviro == 'PRD') } }
            steps {
                runTests(app_url[2], mvnGroupId, depArtifactId, mvnVersion, depArtifactPac, true)
            }
            post {
                always { junit '**/failsafe-reports/*.xml' }
                success { cleanWs() }
            }
        }
    }
    post {
        success {
            cleanWs()
            /* clean up tmp directory */
            dir("${workspace}@tmp") { deleteDir() }
            /* clean up script directory */
            dir("${workspace}@script") { deleteDir() }
        }
    }
}
