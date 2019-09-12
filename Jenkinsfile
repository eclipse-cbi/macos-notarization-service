pipeline {
 
   agent any
 
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