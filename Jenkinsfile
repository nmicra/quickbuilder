pipeline {
    agent any
    stages {
		stage ('Sleep Stage') {
            steps {
                echo 'You have 5 seconds to cancel the build'
				sleep 5
            }
        }
        stage ('Compile Stage') {
            steps {
                sh 'mvn clean install'
            }
        }
        stage ('Build Docker Image') {
            steps {
                sh 'docker build --force-rm -t mydocker-repo:8080/docker-local/quickbuilder:dev .'
            }
        }
        stage ('Expose Artifacts') {
         steps {
              archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
          }
         }
         stage ('Deploy to Docker Repo') {
           steps {
               sh 'mvn deploy'
               sh 'docker push mydocker-repo:8080/docker-local/quickbuilder:dev'
            }
          }
         stage ('Deploy to Server') {
            steps {
                sh 'ssh user@machine /somepath/updateImageAndRestart-dev.sh'
            }
         }
    }

}