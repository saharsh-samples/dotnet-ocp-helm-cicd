/**
 * Wait for deployments to finish
 *
 * Params:
 *   - dc : The deployment object on which to wait
 */
def waitForDeploymentToFinish(dc) {

    def keepTrying              = true
    def deploymentTimeoutMins   = 5
    def deploymentTimeoutMillis = deploymentTimeoutMins * 60 * 1000
    def tryTill                 = System.currentTimeMillis() + deploymentTimeoutMillis

    while (keepTrying) {

        try {

            // Wait till deployment completes
            dc.rollout().status('--request-timeout=' + deploymentTimeoutMins + 'm')
            keepTrying = false

        } catch (err) {

            if (System.currentTimeMillis() < tryTill) {

                // ignore errors till time out
                echo "Ignoring errors till timeout: ${err}"

            } else {

                // bubble up the error if time out has occurred
                throw err

            }

        }

    }
}

/**
 * Build a new image tag using the specified build config
 *
 * Params:
 *   - insecureCluster      : Set to true if cluster URL is HTTPS but verification
 *                            should be skipped
 *   - clusterUrl           : Openshift Cluster URL
 *   - clusterAuthToken     : Token used to authenticate to the Openshift Cluster.
 *                            This will be set in the `kubectl` context
 *   - namespace            : Openshift project where the build will take place
 *   - buildConfigName      : Name of the build configuration to execute
 *   - contextDirectory     : Relative directory path from git repository to pass
 *                            to build execution as the build context
 */
def buildImageStreamTag(insecureCluster, clusterUrl, clusterAuthToken, namespace, buildConfigName, contextDirectory) {

    // If connecting to a server over TLS whose certification should be ignored,
    // use the custom 'insecure' protocol
    if(insecureCluster == 'true') {
        clusterUrl = clusterUrl.replaceFirst('https', 'insecure')
    }

    // build new tag using build configuration
    openshift.withCluster(clusterUrl, clusterAuthToken) {
        openshift.withProject(namespace) {
            result = openshift.startBuild(buildConfigName, '--from-dir=' + contextDirectory, '--follow')
            echo "${result.out}"
        }
    }

}

/**
 * Import an image stream tag. Optionally, after the import, wait for deployment
 * to successfully finish
 *
 * Params:
 *   - insecureCluster      : Set to true if cluster URL is HTTPS but verification
 *                            should be skipped
 *   - clusterUrl           : Openshift Cluster URL
 *   - clusterAuthToken     : Token used to authenticate to the Openshift Cluster.
 *                            This will be set in the `kubectl` context
 *   - namespace            : Openshift project to use as the context
 *   - sourceImageStreamTag : Source image stream tag to import
 *   - targetImageStreamTag : Destination image stream tag
 *   - deploymentConfigName : Name of the deployment configuration to watch. Leave
 *                            null, if no deployment to watch
 */
def importTagAndDeploy(insecureCluster, clusterUrl, clusterAuthToken, namespace, sourceImageStreamTag, targetImageStreamTag, deploymentConfigName) {

    // If connecting to a server over TLS whose certification should be ignored, use
    // the custom 'insecure' protocol
    if(insecureCluster == 'true') {
        clusterUrl = clusterUrl.replaceFirst('https', 'insecure')
    }

    openshift.withCluster(clusterUrl, clusterAuthToken) {
        openshift.withProject(namespace) {

            // import tag
            result = openshift.raw('tag', sourceImageStreamTag, targetImageStreamTag)
            echo "${result.out}"

            if (deploymentConfigName != null) {

                // deployment is triggered automatically. wait for it to finish
                def dc = openshift.selector('dc', deploymentConfigName)
                waitForDeploymentToFinish(dc)

            }
        }
    }

}

return this
