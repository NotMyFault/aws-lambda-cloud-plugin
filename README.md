# AWS Lambda Cloud Plugin for Jenkins

[![Build Status](https://ci.jenkins.io/job/Plugins/job/aws-lambda-cloud-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/aws-lambda-cloud-plugin/job/master/)

## About

This Jenkins plugin uses [AWS Lambda](https://docs.aws.amazon.com/lambda/latest/dg/welcome.html) to host jobs execution.

Jenkins delegates to AWS Lambdas the execution of the builds on Lambda based agents (runtimes).
Each Jenkins build is executed on a Lambda execution that is released at the end of the build.

-   use [GitHub Issues](https://github.com/jenkinsci/aws-lambda-cloud-plugin/issues) to report issues / feature requests

## Limitations

Given the limitations of the AWS Lambda engine :
- jobs can't exceed a duration of 15 minutes
- jobs can't use more than 512 MB of storage
