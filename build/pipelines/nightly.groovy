//!/usr/bin/env groovy

// Get / Set release name
// TODO - remove hardcoded value
def  version = '2.4.0' //env.getProperty("version")
echo version

pipeline {
    agent any
    stages {
        stage('Cleanup') {
            steps {
                script {
                    // Cleanup previous stuff
                    sh("rm -rf scm")
                    sh("rm -rf builds")

                    // Cleanup jlike git folder, files
                    sh("rm -rf .git")
                    sh("rm -rf .gitlab/merge_request_templates")
                    sh("rm -rf build")

                    // Make directories needed to generate build
                    sh("mkdir builds")
                    sh("mkdir scm")
                }
            }
        }

        stage('Checkout') {
            steps {
                script {
                    // This is important, we need clone into different folder here,
                    // Because, as part of tag based pull, we will be cloning same repo again
                    echo env.getProperty("myParamName")
                }
            }
        }

        stage('Cleanup-repos') {
            steps {
                script {
                    def props = readJSON file: 'scm/build/package.json'

                    props['subextensions'].eachWithIndex { item, index ->
                       // cleaup subextensions
                       sh("rm -rf $item.name")
                    }

                }
            }
        }

        stage('Init') {
            steps {
                script {
                    def props = readJSON file: 'scm/build/package.json'

                    // Do clone all subextensions repos by checking out corresponding release branch
                    props['subextensions'].eachWithIndex { item, index ->
                       sh("git clone --branch " + props['versions'][version][item.name]["branch"] + " --depth 1 $item.repoUrl")
                    }
                }
            }
        }

        stage('Copy files & Make zip of subextension') {
            steps {
                script {
                    def props = readJSON file: 'scm/build/package.json'

                    // Copy core files
                    sh("cp -r " + props["core_files"].src + " " + props['core_files'].dest)

                    // Copy Make the zips
                    props['subextensions'].eachWithIndex { item, index ->
                       sh("mkdir -p " + item.dest)

                        if (item.include && item.exclude)
                        {
                            error "Use either include or exclude. Both together not supported!."
                        }

                       def simple_copy = 0

                       if (item.include)
                       {
                            def count = item.include.size()

                            if (count > 0)
                            {
                                def include = item.include.collect { "$item.src/$it" }.join(' ')
                                sh("cp -r $include $item.dest")
                            }
                            else
                            {
                                simple_copy = 1
                            }
                       }
                       else if(item.exclude)
                       {
                           def count = item.exclude.size()

                            if (count > 0)
                            {
                                def exclude = item.exclude.collect { "--exclude $it" }.join(' ')
                                sh("rsync -avz $exclude $item.src $item.dest")
                            }
                            else
                            {
                                simple_copy = 1
                            }
                       }
                       else
                       {
                           simple_copy = 1
                       }

                        if (simple_copy == 1)
                        {
                            sh("cp -r $item.src  $item.dest")
                        }

                        // Create subextension zip and remove folder which is not needed after zip
                       if (item.format == "zip")
                       {
                            sh("cd  $item.dest  && zip -rq ../$item.zip_name" + ".zip .")
                            sh("rm -rf  $item.dest")
                       }
                    }
                }
            }
        }

        stage('Build pacakge') {
            steps {
                script {
                    // // Get commit id
                    // // @TODO - needs to define shortGitCommit at global level
                    def gitCommit      = ''
                    def shortGitCommit = ''
                    def props = readJSON file: 'scm/build/package.json'

                    // // For branch based build - we need the revision number of tag checked out,
                    // Custom DIR
                    dir('scm') {
                        gitCommit      = sh(returnStdout: true, script: 'git rev-parse HEAD').trim().take(8)
                        shortGitCommit = gitCommit[0..7]
                        echo gitCommit
                        echo shortGitCommit
                    }

                    // Now we are good to create zip for component
                    sh('cd builds && zip -rq ../' + props['package_name'] + '_v' + version + "_" + shortGitCommit + '.zip .')

                    archiveArtifacts props['package_name'] + '_v' + version + "_" + shortGitCommit + '.zip'
                }
            }
        }

        stage('Cleanup folders') {
             steps {
                 script {
                    // Cleanup, so next time we get fresh files
                    sh("rm -r builds")
                 }
             }
         }
    }
}
