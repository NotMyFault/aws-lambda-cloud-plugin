# AWS Lambda Cloud Plugin for Jenkins

[![Build Status](https://ci.jenkins.io/job/Plugins/job/aws-lambda-cloud-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/aws-lambda-cloud-plugin/job/master/)
![Jenkins Plugins](https://img.shields.io/jenkins/plugin/v/aws-lambda-cloud)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/aws-lambda-cloud-plugin.svg?label=changelog)](https://github.com/jenkinsci/aws-lambda-cloud-plugin/releases/latest)
![CI Status](https://github.com/jenkinsci/aws-lambda-cloud-plugin/workflows/CI/badge.svg)
![GitHub](https://img.shields.io/github/license/jenkinsci/aws-lambda-cloud-plugin?color=blue)

## About

This Jenkins plugin uses [AWS Lambda](https://docs.aws.amazon.com/lambda/latest/dg/welcome.html) to host jobs execution.

Jenkins delegates to AWS Lambdas the execution of the builds on Lambda based agents (runtimes).
Each Jenkins build is executed on a Lambda execution that is released at the end of the build.

-   use [GitHub Issues](https://github.com/jenkinsci/aws-lambda-cloud-plugin/issues) to report issues / feature requests

## Limitations

Given the limitations of the AWS Lambda engine :
- jobs can't exceed a duration of 15 minutes
- jobs can't use more than 512 MB of storage

## No delay provisioning

By default Jenkins do estimate load to avoid over-provisioning of cloud nodes.
This plugin will use its own provisioning strategy by default, with this strategy, a new node is created on Lambda as soon as NodeProvisioner detects need for more agents.
In worse scenarios, this will results in some extra nodes provisioned on Lambda, which will be shortly terminated.

If you want to turn off this Strategy you can set SystemProperty `io.jenkins.plugins.aws.lambda.cloud.lambdaCloudProvisionerStrategy.disable=true`

## How-to

### Deploy a Lambda Agent

[View details](./agent/README.md)

### Configure this plugin as code

Basic configuration using default credentials and default region if Jenkins is running as an ECS task or an EC2 instance with 2 samples lambdas :
- `jnlp-lambdas-v1-agentGitBash` with label `lambda-git`
- `jnlp-lambdas-v1-agentGitBashNode` with label `lambda-node`

```groovy
import io.jenkins.plugins.aws.lambda.cloud.LambdaCloud;
import io.jenkins.plugins.aws.lambda.cloud.LambdaFunction;

import jenkins.model.Jenkins

jenkins = jenkins.model.Jenkins.get()

c = new LambdaCloud("aws-lambdas", null, '')
f = new LambdaFunction('jnlp-lambdas-v1-agentGitBash', "lambda-git");
f2 = new LambdaFunction('jnlp-lambdas-v1-agentGitBashNode', "lambda-node");
c.setFunctions([f, f2]);
jenkins.clouds.add(c);
jenkins.save()
```
