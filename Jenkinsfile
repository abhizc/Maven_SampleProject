pipeline {
    agent any
    stages {
        stage ('Test') {
            steps {
                script{
                    def remote = [:]
                    remote.name = 'uv1708'
                    remote.host = 'uv1708.emea.eu.int'
                    remote.user = 'serveradmin'
                    remote.password = '0hZXV8S1'
                    remote.allowAnyHosts = true
                    stage('Remote SSH') {
                        sshCommand remote: remote, command: "ls -lrt"
                        }
                    }
                }
        }
    }
}
