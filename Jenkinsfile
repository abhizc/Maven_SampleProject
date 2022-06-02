pipeline {
    agent any
    stages {
        stage ('First') {     
            steps {
                  sshagent (credentials: ['798ecc2e-d05f-443a-9a04-83fbeb3a37a5']) {
                    sh "ssh -o StrictHostKeyChecking=no -l serveradmin uv1708.emea.eu.int 'whoami'"
                    }
                }
    }
    }
}
