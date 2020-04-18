package io.jenkins.plugins.aws.lambda.cloud;

import hudson.model.Node;
import jenkins.model.Jenkins;

import java.util.concurrent.Callable;

public class LambdaCloudProvisioningCallback implements Callable<Node> {

    private final LambdaCloud cloud;
    private final LambdaFunction function;
    private final String nodeName;
    private final String label;

    LambdaCloudProvisioningCallback(LambdaCloud cloud, LambdaFunction function, String nodeName, String label) {
        this.cloud = cloud;
        this.function = function;
        this.nodeName = nodeName;
        this.label = label;
    }

    public Node call() throws Exception {
        LambdaComputerLauncher launcher = new LambdaComputerLauncher(cloud, function);
        LambdaNode agent = new LambdaNode(cloud, label, nodeName, launcher);
        Jenkins.getActiveInstance().addNode(agent);
        return agent;
    }
}
