// Reusable build function
def buildProtcolParser() {
    sh """
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
                            ws("v8-clang") {
                                sh 'echo "=== v8-clang Workspace Debug ==="'
                                sh 'echo "Workspace: $(pwd)"'
                                sh 'echo "User: $(whoami)"'
                                sh 'echo "Permissions: $(ls -la)"'
                                sh 'chmod -R 755 . || true'
                                checkout scm
                                sh 'echo "After checkout: $(ls -la)"'
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
                            ws("v8-gcc") {
                                sh 'echo "=== v8-gcc Workspace Debug ==="'
                                sh 'echo "Workspace: $(pwd)"'
                                sh 'echo "User: $(whoami)"'
                                sh 'echo "Permissions: $(ls -la)"'
                                sh 'chmod -R 755 . || true'
                                checkout scm
                                sh 'echo "After checkout: $(ls -la)"'
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
                            ws("latest-clang") {
                                sh 'echo "=== latest-clang Workspace Debug ==="'
                                sh 'echo "Workspace: $(pwd)"'
                                sh 'echo "User: $(whoami)"'
                                sh 'echo "Permissions: $(ls -la)"'
                                sh 'chmod -R 755 . || true'
                                checkout scm
                                sh 'echo "After checkout: $(ls -la)"'
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
                            ws("latest-gcc") {
                                sh 'echo "=== latest-gcc Workspace Debug ==="'
                                sh 'echo "Workspace: $(pwd)"'
                                sh 'echo "User: $(whoami)"'
                                sh 'echo "Permissions: $(ls -la)"'
                                sh 'chmod -R 755 . || true'
                                checkout scm
                                sh 'echo "After checkout: $(ls -la)"'
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
                            ws("v8-clang") {
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
                            ws("v8-gcc") {
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
                            ws("latest-clang") {
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
                            ws("latest-gcc") {
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
                    sh 'docker image prune -f || true'
                }
            }
        }
    }
}

