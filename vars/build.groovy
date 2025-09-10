// Reusable build function
def buildProtcolParser(buildDir) {
    sh """
        # Copy source to isolated directory for this build
        rm -rf ${buildDir}-src
        cp -r . ${buildDir}-src
        cd ${buildDir}-src
        
        rm -rf build
        mkdir build
        cd build
        cmake ..
        cmake --build . -j \$(nproc)
    """
}

// Reusable test function
def runBtest(buildDir) {
    sh """
        cd ${buildDir}-src/testing
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
            stage('Checkout') {
                steps {
                    checkout scm
                }
            }
            
            stage('Pre-pull Docker Images') {
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
                            buildProtcolParser('build-v8-clang')
                            stash includes: 'build-v8-clang-src/**', name: 'build-v8-clang'
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
                            buildProtcolParser('build-v8-gcc')
                            stash includes: 'build-v8-gcc-src/**', name: 'build-v8-gcc'
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
                            buildProtcolParser('build-latest-clang')
                            stash includes: 'build-latest-clang-src/**', name: 'build-latest-clang'
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
                            buildProtcolParser('build-latest-gcc')
                            stash includes: 'build-latest-gcc-src/**', name: 'build-latest-gcc'
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
                            unstash 'build-v8-clang'
                            runBtest('build-v8-clang')
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
                            unstash 'build-v8-gcc'
                            runBtest('build-v8-gcc')
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
                            unstash 'build-latest-clang'
                            runBtest('build-latest-clang')
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
                            unstash 'build-latest-gcc'
                            runBtest('build-latest-gcc')
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
                    sh 'docker image prune -f || true'
                }
            }
        }
    }
}

