pipeline {
    agent any
    stages {
        stage ('First') {
            steps {
                remote = [:]
                remote.name = "uv1708.emea.eu.int"
                remote.host = "uv1708.emea.eu.int"
                remote.allowAnyHosts = true
                remote.failOnError = true
                withCredentials([usernamePassword(credentialsId: '798ecc2e-d05f-443a-9a04-83fbeb3a37a5', passwordVariable: '0hZXV8S1', usernameVariable: 'serveradmin')]) {
                    remote.user = username
                    remote.password = password
                    }
                sshPut remote: remote, from: 'myfile', into: '/home/serveradmin/'
                
            }
        }
    }
}
