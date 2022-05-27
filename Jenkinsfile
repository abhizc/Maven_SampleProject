pipeline {
    agent any

    tools {
        maven "Maven_385"
        jdk "JDK 9"
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
                sh 'mvn clean package'
            }
            
            }
        }
     }
}
