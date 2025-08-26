// Wrapper method so all CISAGOV repos can share single jenkinsfile build ruleset
def call() {
    pipeline {
        agent {
            label {
                label 'rhel9'
            }
        }
        stages {
            stage('Build Clang') {
                agent {
                    docker { 
                        image 'ghcr.io/mmguero/zeek:latest'
                        args '--user root --entrypoint='
                    }
                }

                steps {
                    sh """
                        rm -rf build
                        mkdir build
                        cd build

                        cmake ..
                        cmake --build . -j \$(nproc)

                        cd ../testing
                        btest
                    """
                }
            }
            stage('Build GCC') {
                agent {
                    docker { 
                        image 'ghcr.io/hanspeterson33/zeek:latest'
                        args '--user root --entrypoint='
                    }
                }

                steps {
                    sh """
                        rm -rf build
                        mkdir build
                        cd build

                        cmake ..
                        cmake --build . -j \$(nproc)

                        cd ../testing
                        btest
                    """
                }
            }
        }
    }
}

