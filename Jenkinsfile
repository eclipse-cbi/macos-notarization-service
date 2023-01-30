pipeline {

  agent any

  tools {
    jdk 'temurin-jdk17-latest'
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
  }

  stages {
    stage('Prepare package') {
      steps {
        sh '''
          ./mvnw package
        '''
      }
    }
  }
}