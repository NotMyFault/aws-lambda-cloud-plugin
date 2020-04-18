package io.jenkins.plugins.aws.lambda.cloud;

import com.amazonaws.SdkClientException;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.lambda.AWSLambda;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is the root class that contains all configuration state about the
 * Lambda cloud agents. Jenkins calls
 * {@link LambdaCloud#provision(Label label, int excessWorkload)} on this
 * class to create new nodes.
 *
 * @author jlamande
 */
public class LambdaCloud extends Cloud {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaCloud.class);

    private static final int DEFAULT_AGENT_TIMEOUT = 60;

    private static final int DEFAULT_MAX_CONCURRENT_EXECUTIONS = 2;

    private static final String DEFAULT_REGION = "us-east-1";

    static {
        clearAllNodes();
    }

    @Nonnull
    private final String credentialsId;

    @Nonnull
    private final String region;

    private String jenkinsUrl;

    private int maxConcurrentExecutions;

    private int agentTimeout;

    private List<LambdaFunction> functions;

    /**
    * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/lambda/AWSLambda.html
    */
    private transient AWSLambda client;

    /**
    * Constructor for LambdaCloud.
    *
    * @param name          the name of the cloud or null if you want it
    *                      auto-generated.
    * @param credentialsId the credentials ID to use or null/empty if pulled from
    *                      environment.
    * @param region        the AWS region to use.
    * @throws InterruptedException if any.
    */
    @DataBoundConstructor
    public LambdaCloud(@Nonnull String name, @Nullable String credentialsId,
        @Nonnull String region) throws InterruptedException {
        // TODO: clouds.size is not really accurate for default name. rather use size of LambdaCloud clouds
        //super(StringUtils.isNotBlank(name) ? name : "lambda_" + Jenkins.getActiveInstance().clouds.size());
        super(name);
        this.credentialsId = StringUtils.defaultIfBlank(credentialsId, "");
        if (StringUtils.isBlank(region)) {
            this.region = getDefaultRegion();
        } else {
            this.region = region;
        }
        this.client = LambdaClient.buildClient(this.credentialsId, this.region);
        LOGGER.info("[AWS Lambda Cloud]: Initializing Cloud: {}", this);
    }

    public static @Nonnull LambdaCloud getByName(@Nonnull String name) throws IllegalArgumentException {
        Cloud cloud = Jenkins.getActiveInstance().clouds.getByName(name);
        if (cloud instanceof LambdaCloud) return (LambdaCloud) cloud;
        throw new IllegalArgumentException("'" + name + "' is not an AWS Lambda cloud but " + cloud);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("%s", name);
    }


    public static String getDefaultRegion() {
        try {
            return new DefaultAwsRegionProviderChain().getRegion();
        } catch (SdkClientException exc) {
            return DEFAULT_REGION;
        }
    }

    public static int getDefaultAgentTimeout() {
        return DEFAULT_AGENT_TIMEOUT;
    }

    /**
    * Getter for the field <code>region</code>.
    *
    * @return a {@link String} object.
    */
    @Nonnull
    public String getRegion() {
        return region;
    }

    /**
    * Getter for the field <code>jenkinsUrl</code>.
    *
    * @return a {@link String} object.
    */
    @Nonnull
    public String getJenkinsUrl() {
        if (StringUtils.isNotBlank(jenkinsUrl)) {
            return jenkinsUrl;
        } else {
            JenkinsLocationConfiguration config = JenkinsLocationConfiguration.get();
            if (config != null) {
                return StringUtils.defaultIfBlank(config.getUrl(), "unknown");
            }
        }
        return "unknown";
    }

    /**
    * Setter for the field <code>jenkinsUrl</code>.
    *
    * @param jenkinsUrl a {@link String} object.
    */
    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl) {
        JenkinsLocationConfiguration config = JenkinsLocationConfiguration.get();
        if (config != null && StringUtils.equals(config.getUrl(), jenkinsUrl)) {
            return;
        }
        this.jenkinsUrl = jenkinsUrl;
    }

    /**
    * Getter for the field <code>agentTimeout</code>.
    *
    * @return a int.
    */
    @Nonnull
    public int getAgentTimeout() {
        return agentTimeout == 0 ? DEFAULT_AGENT_TIMEOUT : agentTimeout;
    }

    /**
    * Setter for the field <code>agentTimeout</code>.
    *
    * @param agentTimeout a int.
    */
    @DataBoundSetter
    public void setAgentTimeout(int agentTimeout) {
        this.agentTimeout = agentTimeout;
    }

    /**
     * Getter for the field <code>maxConcurrentExecutions</code>.
     *
     * @return a int.
     */
    @Nonnull
    public int getMaxConcurrentExecutions() {
        return maxConcurrentExecutions == 0 ? DEFAULT_MAX_CONCURRENT_EXECUTIONS : maxConcurrentExecutions;
    }

    /**
     * Setter for the field <code>maxConcurrentExecutions</code>.
     *
     * @param maxConcurrentExecutions a int.
     */
    @DataBoundSetter
    public void setMaxConcurrentExecutions(int maxConcurrentExecutions) {
        this.maxConcurrentExecutions = maxConcurrentExecutions;
    }

    @Nonnull
    public List<LambdaFunction> getFunctions() {
        return functions != null ? functions : Collections.<LambdaFunction> emptyList();
    }

    @DataBoundSetter
    public void setFunctions(List<LambdaFunction> functions) {
        this.functions = functions;
    }

    /**
    * Getter for the field <code>client</code>.
    *
    * @return a {@link com.amazonaws.services.lambda.AWSLambda} object.
    */
    public synchronized AWSLambda getClient() {
        //if (this.client == null) {
        //    this.client = LambdaClient.buildClient(credentialsId, region);
        //}
        return this.client;
    }

    private transient long lastProvisionTime = 0;

    /**
     * Clear all CodeBuilder nodes on boot-up because these cannot be permanent.
     */
    private static void clearAllNodes() {
        try {
            List<Node> nodes = Jenkins.getActiveInstance().getNodes();
            if (nodes.size() == 0) {
                return;
            }

            LOGGER.info("[AWS Lambda Cloud]: Clearing all previous Lambda nodes...");
            for (final Node n : nodes) {
                if (n instanceof LambdaNode) {
                    try {
                        ((LambdaNode) n).terminate();
                    } catch (InterruptedException | IOException e) {
                        LOGGER.error("[AWS Lambda Cloud]: Failed to terminate agent '{}'", n.getDisplayName(), e);
                    }
                }
            }
        } catch(IllegalStateException e) {
            LOGGER.warn("Illegal state : {}", e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean canProvision(Label label) {
        return getFunction(label)!= null;
    }

    private LambdaFunction getFunction(Label label) {
        if (label == null) {
            return null;
        }
        for (LambdaFunction f : getFunctions()) {
            if (label.matches(f.getLabelSet())) {
                return f;
            }
        }
        return null;
    }

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        List<NodeProvisioner.PlannedNode> nodesList = new ArrayList<NodeProvisioner.PlannedNode>();

        // guard against double-provisioning with a 500ms cooldown clock
        long timeDiff = System.currentTimeMillis() - lastProvisionTime;
        if (timeDiff < 500) {
            LOGGER.info("[AWS Lambda Cloud]: Provision of {} skipped, still on cooldown ({}ms of 500ms)", excessWorkload,
                timeDiff);
            return nodesList;
        }

        try {
            LOGGER.debug("Asked to provision {} node(s) for: {}", excessWorkload, label);
            final LambdaFunction function = getFunction(label);
            // final LambdaFunction function = new LambdaFunction(this.functionName, label.getName());

            for (int i = 1; i <= excessWorkload; i++) {
                // String agentName = name + "-" + label.getName() + "-" + RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
                final String suffix = RandomStringUtils.randomAlphabetic(6);
                final String nodeName = String.format("%s.lambda-%s", label.getName(), suffix);
                LOGGER.info("Will provision {}, for label: {}", nodeName, label);
                nodesList.add(
                    new NodeProvisioner.PlannedNode(
                        nodeName,
                        Computer.threadPoolForRemoting.submit(
                            new LambdaCloudProvisioningCallback(this, function, nodeName, label.getName())
                        ),
                        1
                    )
                );
            }
            lastProvisionTime = System.currentTimeMillis();
            return nodesList;
        } catch (Exception e) {
            LOGGER.warn("Failed to provision Lambda node", e);
        }
        return Collections.emptyList();
    }

    @Extension
    public static class LambdaCloudDescriptorImpl extends LambdaCloudDescriptor{};

}
