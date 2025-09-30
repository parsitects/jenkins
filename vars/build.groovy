def buildAndTestProtocolParser() {
    sh """
        set -ex
        rm -rf build
        if [ -f ./configure ]; then
            ./configure
            cd build
            make
        else
            mkdir build
            cd build
            cmake ..
            cmake --build . -j \$(nproc)
        fi
        cd ../testing
        btest
    """
}

def call() {
    pipeline {
        agent {
            label 'rhel9'
        }

        options {
            skipDefaultCheckout()
        }

        stages {
            stage('Pull Docker Images') {
                parallel {
                    stage('Pull v8.0.0 Images') {
                        steps {
                            script {
                                sh 'docker pull ghcr.io/mmguero/zeek:v8.0.0-clang'
                                sh 'docker pull ghcr.io/mmguero/zeek:v8.0.0-gcc'
                            }
                        }
                    }
                    stage('Pull Latest Images') {
                        steps {
                            script {
                                sh 'docker pull ghcr.io/mmguero/zeek:latest-clang'
                                sh 'docker pull ghcr.io/mmguero/zeek:latest-gcc'
                            }
                        }
                    }
                }
            }

            stage('Build and Test Matrix') {
                parallel {
                    stage('v8.0.0-clang') {
                        agent {
                            docker { 
                                image 'ghcr.io/mmguero/zeek:v8.0.0-clang'
                                args '--user root --entrypoint='
                                reuseNode true
                            }
                        }
                        steps {
                            dir("v8-clang") {
                                checkout scm
                                buildAndTestProtocolParser()
                            }
                        }
                    }
                    stage('v8.0.0-gcc') {
                        agent {
                            docker { 
                                image 'ghcr.io/mmguero/zeek:v8.0.0-gcc'
                                args '--user root --entrypoint='
                                reuseNode true
                            }
                        }
                        steps {
                            dir("v8-gcc") {
                                checkout scm
                                buildAndTestProtocolParser()
                            }
                        }
                    }
                    stage('latest-clang') {
                        agent {
                            docker { 
                                image 'ghcr.io/mmguero/zeek:latest-clang'
                                args '--user root --entrypoint='
                                reuseNode true
                            }
                        }
                        steps {
                            dir("latest-clang") {
                                checkout scm
                                buildAndTestProtocolParser()
                            }
                        }
                    }
                    stage('latest-gcc') {
                        agent {
                            docker { 
                                image 'ghcr.io/mmguero/zeek:latest-gcc'
                                args '--user root --entrypoint='
                                reuseNode true
                            }
                        }
                        steps {
                            dir("latest-gcc") {
                                checkout scm
                                buildAndTestProtocolParser()
                            }
                        }
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    sh 'docker container prune -f || true'
                    sh 'docker volume prune -f || true'
                    sh 'docker network prune -f || true'
                    sh 'docker image prune -f || true'
                }
            }
        }
    }
}