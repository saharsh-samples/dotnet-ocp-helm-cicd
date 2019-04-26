# .NET + Openshift + Helm Reference Project

This project is intended as a reference project for containerized .NET applications deployed to Openshift using CI/CD pipelines. The project contains a fully functional ASP.NET 2.2 WebAPI web service, Openshift templates and Helm charts needed to deploy the application, and a Jenkinsfile that can be used to stand up a CI/CD pipeline for the project.

## Sample .NET Application

The sample application is a very basic ASP.NET 2.2 WebAPI project. Details regarding the application itself are contained in the [sample-dotnet-app](sample-dotnet-app) subdirectory.

## Openshift Deployment

There is reference for deploying the application manually using Helm. Consult the [`deployment`](deployment) directory README for further details.

## CI/CD Pipeline

The repo also contains a [Jenkinsfile](Jenkinsfile) that can be used to setup a CI/CD pipeline for the application. The pipeline is intended to run as changes to branches are pushed to the origin repository of the application (e.g. git server hosted by Github or Bitbucket). The release flow of the application only executes for `master` branch commits. The release flow includes building the deployable container image, deploying the application to intended environments, and automating versioning related tasks (e.g. creating git tags and incrementing application version). For feature branches, the pipeline makes sure there are no compilation and test failures by running the multi stage [`Dockerfile`](sample-dotnet-app/Dockerfile) build, but goes no further.

### Branching Strategy

The pipeline assumes the repository uses the [Trunk based development](https://trunkbaseddevelopment.com) branching strategy. In this branching model, the main branch is `master`. All feature branches are created from this branch. Upon completion, the feature branches are merged back into the `master` branch and deleted.

### Versioning

The pipeline automates majority of the activity related to versioning of the application. The version is expected to follow [typical semantic versioning](https://en.wikipedia.org/wiki/Software_versioning#Sequence-based_identifiers), and the version of the software is stored in the `appVersion` field of the [`Chart.yaml`](deployment/helm-k8s/Chart.yaml) file used for Helm installs. This version is expected to change under two circumstances:

1. It is automatically changed by the pipeline as a result of changes pushed to the `master` branch. In this case, the pipeline tags the `HEAD` of the `master` branch with the appVersion value contained in the [`Chart.yaml`](deployment/helm-k8s/Chart.yaml) file. After pushing the tag to the origin repository, the pipeline then increments the third digit of the semantic version by `one`, and pushes the new `HEAD` of `master` to the origin repository.

1. It is manually changed by the maintainers of the application if the major and/or minor versions need to be incremented. The change will be applied like any other change to the application source - using a feature branch that is merged into `master`. 

### Pipeline Stages

Following sections describe each stage of the CI/CD pipeline and indicate the branch(es) for which the stage is run.

#### Initialize

**Run for:** All branches

The intention of this stage is to do any initialization that modifies the pipeline environment before running any of the other stages. Currently, this includes the following:

* Load shared Groovy modules - These are reusable functions written in Groovy that encapsulate high level tasks related to a specific tool or concern. They are organized in files, and each file contains one or more functions that belong to the same grouping (e.g. Helm related tasks). Each file is loaded as a namespace and bound to an appropriately named field in the `modules` global variable.

* Load Kubernetes pod templates - These are YAML files describing the pod used to run certain stages in the pipeline. For example, deployment stages are run with the [`ocp-helm-agent`](.jenkins/agents/ocp-helm-agent.yml) pod template. These templates are stored in external files to reduce clutter from the main `Jenkinsfile`. They are loaded in this stage and bound to environment variables so they can be used by later stages.

* Set necessary environment variables - Following environment variables are set so they can be used by later stages.
    * **shouldJobRun** - This is set to `false` if the committer of the git commit triggering the current job instance is `Jenkins`. `Jenkins` is set as the committer by the pipeline when automatically incrementing the application version on the `master` branch as part of the release process. This makes sure the execution of the release process is skipped for these commits.
    * **buildVersion** - The build version is largely based on the version stored in the [`Chart.yaml`](deployment/helm-k8s/Chart.yaml) file. For `master` branch, the build version is exactly that. For other branches, the build version is determined by appending a '-' followed by the name of the branch (e.g. 1.0.0-develop or 2.3.5-some-feature-request).
    * **buildVersionWithHash** - This is a combination of `buildVersion` and the short git commit hash. It is injected into the `sample-dotnet-app` instance itself as the `APP_VERSION` environment variable. This value is echoed back by the app in the `/info` endpoint.

#### Feature branch build

**Run for:** Feature branches

Feature branches are not part of the release process. Therefore, for commits to feature branches, only this stage of the pipeline is run. In this stage, the `buildah` tool is used to run a multi-stage `Dockerfile` build. The intention is to have the first stage of the `Dockerfile` compile code, run unit and integration tests, and create .NET binaries. The second stage then packages the built binaries from the first stage into a deployable container image. The stage doesn't go any further than this, as the release strategy for this application doesn't automatically deploy images from feature branches to any shared environments.

This stage is run using the [`buildah-agent`](.jenkins/agents/buildah-agent.yml) which contains the [`buildah`](https://github.com/containers/buildah) tool.

#### Deliver Release

**Run for:** Only `master` branch

This stage is the first step in the release process for this repository. Following steps are included in this stage:

* Install/upgrade the application's Helm charts in the `development` namespace in the target OCP cluster. This deploys the application's Openshift objects (including its build configuration). However, since no container image is built yet, actual application pods are not yet spawned.

* Start a build using the build configuration deployed in the previous step to build the application container image and deliver it to the `development` namespace image stream. The build itself is done using the same multi stage `Dockerfile` that was used in the feature branch build stage. However, the key difference is the build is only triggered from the Jenkins agent and is actually executed in the `development` namespace of the target OCP cluster. The delivered image is tagged with the `buildVersion` environment variable.

This stage is run using the [`ocp-helm-agent`](.jenkins/agents/ocp-helm-agent.yml) which contains the [Kubernetes](https://kubernetes.io/docs/reference/kubectl/overview/), [Helm](https://github.com/helm/helm/releases/tag/v2.13.1), and [OCP](https://docs.openshift.com/container-platform/3.11/cli_reference/get_started_cli.html) CLI clients.

#### Tag and Increment Version

**Run for:** Only `master` branch

After a successful "Deliver Release" stage, this stage runs the automated versioning tasks if the pipeline is running for the `master` branch. Completion of this stage concludes the delivery of the release. This stage includes the following tasks:

* A Git tag is created using the build version and pushed to the origin repository. 

* Then, the pipeline modifies the `HEAD` of  the `master` branch by updating the `appVersion` field in the [`Chart.yaml`](deployment/helm-k8s/Chart.yaml) file by incrementing the last digit by `one`, commits the changes, and pushes the changes to the origin repository.

This stage is run using `agent any` as no specific Kubernetes pods need to be spawned to execute this stage.

#### Deploy to Dev

**Run for:** Only `master` branch

After a successful "Tag and Increment Version" stage, the remaining stages in the pipeline are concerned with deploying the released version in shared environments. This stage is the first in sequence and deploys the released version of the application to the `development` environment. This is accomplished by tagging the delivered container image in the `development` namespace image stream as `latest`. The deployment configuration for the application is configured to trigger deployments if the image associated with the `latest` tag in the local image stream changes. Therefore, this stage does exactly that by running an `oc tag` command.

This stage is run using the [`ocp-helm-agent`](.jenkins/agents/ocp-helm-agent.yml) which contains the [OCP](https://docs.openshift.com/container-platform/3.11/cli_reference/get_started_cli.html) CLI client.

#### Promote to QA

**Run for:** Only `master` branch

After a successful "Deploy to Dev" stage, the released version is ready for promotion to `QA` environment. Following steps are included in this stage:

* Install/upgrade the application's Helm charts in the `QA` namespace in the target OCP cluster. This deploys the application's Openshift objects (**NOT** including its build configuration). However, since no container image is built yet, actual application pods are not yet spawned.

* Tag the delivered container image in the `development` namespace image stream as `latest` in the image stream in `qa` namespace. Similarly as in `development` namespace, this action triggers the deployment of the new release in  `qa` namespace.

This stage is run using the [`ocp-helm-agent`](.jenkins/agents/ocp-helm-agent.yml) which contains the [Kubernetes](https://kubernetes.io/docs/reference/kubectl/overview/), [Helm](https://github.com/helm/helm/releases/tag/v2.13.1), and [OCP](https://docs.openshift.com/container-platform/3.11/cli_reference/get_started_cli.html) CLI clients.

#### Confirm Promotion to Production

**Run for:** Only `master` branch

After a successful "Promote to QA" stage, the pipeline halts waiting for a manual confirmation from an authorized **Jenkins** account. If directed to proceed, the pipeline will proceed to next stage where the release is promoted to `production` namespace.

This stage is not run using dynamic Kubernetes agents as the job is simply halting till user input.

#### Promote to Production

**Run for:** Only `master` branch

After being directed to 'Proceed' in "Confirm Promotion to Production" stage, the released version is ready for promotion to `Production` environment. Following steps are included in this stage:

* Install/upgrade the application's Helm charts in the `Production` namespace in the target OCP cluster. This deploys the application's Openshift objects (**NOT** including its build configuration). However, since no container image is built yet, actual application pods are not yet spawned.

* Tag the delivered container image in the `development` namespace image stream as `latest` in the image stream in `production` namespace. Similarly as in previous namespaces, this action triggers the deployment of the new release in  `production` namespace.

This stage is run using the [`ocp-helm-agent`](.jenkins/agents/ocp-helm-agent.yml) which contains the [Kubernetes](https://kubernetes.io/docs/reference/kubectl/overview/), [Helm](https://github.com/helm/helm/releases/tag/v2.13.1), and [OCP](https://docs.openshift.com/container-platform/3.11/cli_reference/get_started_cli.html) CLI clients.

### Pipeline Setup

The pipeline is created as a "Multi Branch Pipeline" in Jenkins.

#### 

Following dependencies must be met before being able to start using the pipeline.

1. Jenkins configured with the [`Kubernetes plugin`](https://github.com/jenkinsci/kubernetes-plugin).  

1. Jenkins `username and password` credential named `git-auth` that allows Jenkins and the pipeline to fully consume this Git repository.

1. An instance of the `tiller` server installed and running in the `tiller` namespace on the target Kubernetes cluster.

1. The `sample-projects`, `sample-projects-qa`, and `sample-projects-dev` namespaces created in Kubernetes with `tiller` having the ability to manage projects inside them.

1. Jenkins `username and password` credential named `ocp-cluster-auth` that contains the target Openshift cluster URL and authentication token for the service account that will be used to connect to the cluster. This service account needs to have `edit` privileges in the `tiller` namespace.

1. The namespace where Jenkins is deployed should have a `jenkins-privileged` service account that has the ability to run privileged containers. This is needed so that the `buildah` container instance can run as privileged.

#### Pipeline setup on Minishift

Following set of instructions is an example of how to achieve the above setup in a vanilla Minishift instance.

1. Install the Jenkins (ephemeral) service from the provided catalog under `jenkins` namespace.

1. Create a `jenkins-privileged` service account and grant it the ability to run privileged containers. Run the following as a user with cluster admin access.

        oc login -u system:admin
        oc create sa jenkins-privileged -n jenkins
        oc adm policy add-scc-to-user privileged -n jenkins -z jenkins-privileged
        oc login -u developer

1. Install the `tiller` server in Minishift under the `tiller` namespace. See [deployment/README.md](deployment/helm-k8s/README.md) for instructions.

1. Give the `tiller` service account `edit` privileges in `tiller` namespace.

        oc policy add-role-to-user edit -z tiller -n tiller

1. Get the service token for `tiller` service account.

        oc serviceaccounts get-token tiller -n tiller

1. In Jenkins, create the following credentials:

    * `git-auth`: A `username and password` credential that will be used to pull this Git repository.

    * `ocp-cluster-auth` : A `username and password` credential containing the OCP cluster URL and `tiller` service account login token retrieved in previous step.

1. Create the `dev`, `qa`, and `production` namespaces and give the `tiller` service account `edit` privileges.

        oc new-project sample-projects-dev

        oc new-project sample-projects-qa

        oc new-project sample-projects

        oc policy add-role-to-user edit \
            "system:serviceaccount:tiller:tiller" \
            -n sample-projects-dev

        oc policy add-role-to-user edit \
            "system:serviceaccount:tiller:tiller" \
            -n sample-projects-qa

        oc policy add-role-to-user edit \
            "system:serviceaccount:tiller:tiller" \
            -n sample-projects

1. Create a "Multi Branch Pipeline" in Jenkins using this Git repository as the source and `git-auth` credentials created above as the credentials. At a minimum, setup periodic polling of all branches so builds can trigger automatically. However, it is strongly recommended as a best practice to setup webhooks from the repository server so builds are triggered as push events from Git server rather than as a consequence of polling from Jenkins.
