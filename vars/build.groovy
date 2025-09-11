// Reusable build function
def buildProtcolParser() {
    sh """
        set -ex
        rm -rf build
        mkdir build
        cd build
        cmake ..
        cmake --build . -j \$(nproc)
    """
}

// Reusable test function
def runBtest() {
    sh """
        set -ex
        cd testing
        btest
    """
}

// Wrapper method so all CISAGOV repos can share single jenkinsfile build ruleset
def call() {
    pipeline {
        agent {
            label 'rhel9'
        }
        
        environment {
            BUILD_V8_CLANG_SUCCESS = 'false'
            BUILD_V8_GCC_SUCCESS = 'false'
            BUILD_LATEST_CLANG_SUCCESS = 'false'
            BUILD_LATEST_GCC_SUCCESS = 'false'
        }

        options {
            skipDefaultCheckout()
        }

        stages {
            stage('Pre-pull Docker Images & Checkout Source') {
                parallel {
                    stage('Pull v8.0.0 Images') {
                        agent {
                            label 'rhel9'
                        }
                        steps {
                            script {
                                sh 'docker pull ghcr.io/mmguero/zeek:v8.0.0-clang'
                                sh 'docker pull ghcr.io/mmguero/zeek:v8.0.0-gcc'
                            }
                        }
                    }
                    stage('Pull Latest Images') {
                        agent {
                            label 'rhel9'
                        }
                        steps {
                            script {
                                sh 'docker pull ghcr.io/mmguero/zeek:latest-clang'
                                sh 'docker pull ghcr.io/mmguero/zeek:latest-gcc'
                            }
                        }
                    }
                    stage('Checkout v8-clang') {
                        agent {
                            label 'rhel9'
                        }
                        steps {
                            dir("v8-clang") {
                                checkout scm
                                stash includes: '**', name: 'source-v8-clang'
                            }
                        }
                    }
                    stage('Checkout v8-gcc') {
                        agent {
                            label 'rhel9'
                        }
                        steps {
                            dir("v8-gcc") {
                                checkout scm
                                stash includes: '**', name: 'source-v8-gcc'
                            }
                        }
                    }
                    stage('Checkout latest-clang') {
                        agent {
                            label 'rhel9'
                        }
                        steps {
                            dir("latest-clang") {
                                checkout scm
                                stash includes: '**', name: 'source-latest-clang'
                            }
                        }
                    }
                    stage('Checkout latest-gcc') {
                        agent {
                            label 'rhel9'
                        }
                        steps {
                            dir("latest-gcc") {
                                checkout scm
                                stash includes: '**', name: 'source-latest-gcc'
                            }
                        }
                    }
                }
            }
            
            stage('Build Matrix') {
                parallel {
                    stage('Build v8.0.0-clang') {
                        agent {
                            docker { 
                                image 'ghcr.io/mmguero/zeek:v8.0.0-clang'
                                args '--user root --entrypoint='
                                reuseNode true
                            }
                        }
                        steps {
                            dir("v8-clang") {
                                unstash 'source-v8-clang'
                                buildProtcolParser()
                                stash includes: 'build/**', name: 'build-v8-clang'
                            }
                        }
                        post {
                            success {
                                script {
                                    env.BUILD_V8_CLANG_SUCCESS = 'true'
                                }
                            }
                            failure {
                                script {
                                    env.BUILD_V8_CLANG_SUCCESS = 'false'
                                }
                            }
                        }
                    }
                    stage('Build v8.0.0-gcc') {
                        agent {
                            docker { 
                                image 'ghcr.io/mmguero/zeek:v8.0.0-gcc'
                                args '--user root --entrypoint='
                                reuseNode true
                            }
                        }
                        steps {
                            dir("v8-gcc") {
                                unstash 'source-v8-gcc'
                                buildProtcolParser()
                                stash includes: 'build/**', name: 'build-v8-gcc'
                            }
                        }
                        post {
                            success {
                                script {
                                    env.BUILD_V8_GCC_SUCCESS = 'true'
                                }
                            }
                            failure {
                                script {
                                    env.BUILD_V8_GCC_SUCCESS = 'false'
                                }
                            }
                        }
                    }
                    stage('Build latest-clang') {
                        agent {
                            docker { 
                                image 'ghcr.io/mmguero/zeek:latest-clang'
                                args '--user root --entrypoint='
                                reuseNode true
                            }
                        }
                        steps {
                            dir("latest-clang") {
                                unstash 'source-latest-clang'
                                buildProtcolParser()
                                stash includes: 'build/**', name: 'build-latest-clang'
                            }
                        }
                        post {
                            success {
                                script {
                                    env.BUILD_LATEST_CLANG_SUCCESS = 'true'
                                }
                            }
                            failure {
                                script {
                                    env.BUILD_LATEST_CLANG_SUCCESS = 'false'
                                }
                            }
                        }
                    }
                    stage('Build latest-gcc') {
                        agent {
                            docker { 
                                image 'ghcr.io/mmguero/zeek:latest-gcc'
                                args '--user root --entrypoint='
                                reuseNode true
                            }
                        }
                        steps {
                            dir("latest-gcc") {
                                unstash 'source-latest-gcc'
                                buildProtcolParser()
                                stash includes: 'build/**', name: 'build-latest-gcc'
                            }
                        }
                        post {
                            success {
                                script {
                                    env.BUILD_LATEST_GCC_SUCCESS = 'true'
                                }
                            }
                            failure {
                                script {
                                    env.BUILD_LATEST_GCC_SUCCESS = 'false'
                                }
                            }
                        }
                    }
                }
            }
            
            stage('Test Matrix') {
                parallel {
                    stage('Test v8.0.0-clang') {
                        agent {
                            docker { 
                                image 'ghcr.io/mmguero/zeek:v8.0.0-clang'
                                args '--user root --entrypoint='
                                reuseNode true
                            }
                        }
                        when {
                            expression { env.BUILD_V8_CLANG_SUCCESS == 'true' }
                        }
                        steps {
                            dir("v8-clang") {
                                unstash 'source-v8-clang'
                                unstash 'build-v8-clang'
                                runBtest()
                            }
                        }
                    }
                    stage('Test v8.0.0-gcc') {
                        agent {
                            docker { 
                                image 'ghcr.io/mmguero/zeek:v8.0.0-gcc'
                                args '--user root --entrypoint='
                                reuseNode true
                            }
                        }
                        when {
                            expression { env.BUILD_V8_GCC_SUCCESS == 'true' }
                        }
                        steps {
                            dir("v8-gcc") {
                                unstash 'source-v8-gcc'
                                unstash 'build-v8-gcc'
                                runBtest()
                            }
                        }
                    }
                    stage('Test latest-clang') {
                        agent {
                            docker { 
                                image 'ghcr.io/mmguero/zeek:latest-clang'
                                args '--user root --entrypoint='
                                reuseNode true
                            }
                        }
                        when {
                            expression { env.BUILD_LATEST_CLANG_SUCCESS == 'true' }
                        }
                        steps {
                            dir("latest-clang") {
                                unstash 'source-latest-clang'
                                unstash 'build-latest-clang'
                                runBtest()
                            }
                        }
                    }
                    stage('Test latest-gcc') {
                        agent {
                            docker { 
                                image 'ghcr.io/mmguero/zeek:latest-gcc'
                                args '--user root --entrypoint='
                                reuseNode true
                            }
                        }
                        when {
                            expression { env.BUILD_LATEST_GCC_SUCCESS == 'true' }
                        }
                        steps {
                            dir("latest-gcc") {
                                unstash 'source-latest-gcc'
                                unstash 'build-latest-gcc'
                                runBtest()
                            }
                        }
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    // Clean up containers and dangling resources but preserve tagged images for caching
                    sh 'docker container prune -f || true'
                    sh 'docker volume prune -f || true'
                    sh 'docker network prune -f || true'
                    sh 'docker image prune -f --filter "label!=maintainer=mmguero" || true'
                }
            }
        }
    }
}
