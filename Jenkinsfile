pipeline {
    agent any
    tools {
        maven "Maven_385"
    }
    stages {
        stage('Initialize'){
            steps{
                echo "PATH = ${M2_HOME}/bin:${PATH}"
                echo "M2_HOME = /opt/maven"
            }
        }
        stage('Build') {
            steps {
                script {
                sh 'mvn -B -DskipTests clean package'
                }
            }
        }
     }
}
