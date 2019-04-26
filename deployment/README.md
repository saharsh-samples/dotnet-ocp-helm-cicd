# Deploy Using Helm

This directory contains files needed to deploy this application to any Openshift cluster **using [Helm](https://docs.helm.sh/)**.

## Overview

This directory serves as a reference for deploying a typical cloud native web application to Openshift using Helm.

## Dependencies

1. Openshift cluster (or [Minishift](https://www.okd.io/minishift/))

1. Install [`oc`](https://docs.openshift.com/container-platform/3.11/cli_reference/). You will need this to be able to create projects and `kubectl` context outside of Helm.

1. The [`helm`](https://docs.helm.sh/using_helm/#install-helm) client.

## Setup

### Initialize Helm and Install `tiller`

As documented in [official docs](https://docs.helm.sh/using_helm/#initialize-helm-and-install-tiller), before using Helm you need to initialize the client and install `tiller` server in the target cluster. On Openshift, this is accomplished using the following commands. If running on Minishift, you can install using any OCP user. However, on a real OCP cluster, you'll need to use a user with `cluster-admin` access.

NOTE: Determine the version of Helm client by running `helm version`. Replace the `[helm-version]` below with the client version (e.g. `v2.13.1`).

        > oc login [cluster-url]

        > helm init --client-only

        > export TILLER_NAMESPACE=tiller

        > oc new-project $TILLER_NAMESPACE

        > oc project $TILLER_NAMESPACE

        > oc process -f \
          https://github.com/openshift/origin/raw/master/examples/helm/tiller-template.yaml \
          -p TILLER_NAMESPACE="$TILLER_NAMESPACE" \
          -p HELM_VERSION=[helm-version] | \
          oc create -f -

If successful, running `helm version` now will show you status for both client and server.

### Setup Openshift project

Run the following to create an Openshift project (i.e. namespace) for your application and allow Helm's `tiller` server to manage it.

        > oc new-project sample-projects

        > oc policy add-role-to-user edit "system:serviceaccount:${TILLER_NAMESPACE}:tiller" -n sample-projects

## Install Helm charts

After setup is complete, installing the Helm charts is straightforward. The command below installs the [templates](templates) with default values as specified in [values.yaml](values.yaml). To override any values in that file, use the `--set` flag.

        helm install --wait \
            --name dotnet-ocp-helm-sample \
            --namespace sample-projects \
            --set build.deploy=true .

## Build and trigger deployment

The [`buildconfig.yml`](templates/buildconfig.yml) template creates an Openshift build configuration in `sample-projects` that can be used to create and push container images using this repository's [Dockerfile](../sample-dotnet-app/Dockerfile) into the image stream created by Helm install above.

        oc start-build \
            --namespace sample-projects \
            dotnet-ocp-helm-sample \
            --from-dir=../sample-dotnet-app \
            --follow

The build configuration used above uses `Binary` source type and `Docker` build strategy. `Binary` source type means source is provided by uploading source code directly as part of the command (see the `--from-dir` option). `Docker` build strategy means the uploaded source contains a `Dockerfile` at the root directory that is used to build the application's container image. The output container image is then pushed into the `dotnet-ocp-helm-sample:latest` image stream tag. Since the deployment configuration that was created for the app in the same namespace has an image change trigger for `dotnet-ocp-helm-sample:latest` image stream tag, the image push triggers the application's first deployment.
