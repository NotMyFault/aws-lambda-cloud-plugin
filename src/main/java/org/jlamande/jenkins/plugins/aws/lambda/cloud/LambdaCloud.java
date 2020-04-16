package org.jlamande.jenkins.plugins.aws.lambda.cloud;

import com.amazonaws.SdkClientException;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.lambda.AWSLambda;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;

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

    private static final int DEFAULT_AGENT_TIMEOUT = 120;

    private static final String DEFAULT_REGION = "us-east-1";

    static {
        clearAllNodes();
    }

    @Nonnull
    private final String functionName;

    @Nonnull
    private final String credentialsId;

    @Nonnull
    private final String region;

    private String label;

    private String jenkinsUrl;

    private int agentTimeout;

    /**
    * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/lambda/AWSLambda.html
    */
    private transient AWSLambda client;

    /**
    * Constructor for LambdaCloud.
    *
    * @param name          the name of the cloud or null if you want it
    *                      auto-generated.
    * @param functionName   the name of the AWS Lambda function to build from.
    * @param credentialsId the credentials ID to use or null/empty if pulled from
    *                      environment.
    * @param region        the AWS region to use.
    * @throws InterruptedException if any.
    */
    @DataBoundConstructor
    public LambdaCloud(@Nonnull String name, @Nonnull String functionName, @Nullable String credentialsId,
        @Nonnull String region) throws InterruptedException {
        // TODO: clouds.size is not really accurate for default name. rather use size of LambdaCloud clouds
        //super(StringUtils.isNotBlank(name) ? name : "lambda_" + Jenkins.getActiveInstance().clouds.size());
        super(name);

        this.functionName = functionName;
        this.credentialsId = StringUtils.defaultIfBlank(credentialsId, "");
        if (StringUtils.isBlank(region)) {
            this.region = getDefaultRegion();
        } else {
            this.region = region;
        }
        this.client = LambdaClient.buildClient(credentialsId, region);
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
        return String.format("%s<%s>", name, functionName);
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
    * Getter for the field <code>functionName</code>.
    *
    * @return a {@link String} object.
    */
    @Nonnull
    public String getFunctionName() {
        return functionName;
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
    * Getter for the field <code>label</code>.
    *
    * @return a {@link String} object.
    */
    @Nonnull
    public String getLabel() {
        return StringUtils.defaultIfBlank(label, "");
    }

    /**
    * Setter for the field <code>label</code>.
    *
    * @param label a {@link String} object.
    */
    @DataBoundSetter
    public void setLabel(String label) {
        this.label = label;
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
    }

    /** {@inheritDoc} */
    @Override
    public boolean canProvision(Label label) {
        return label == null ? false : label.matches(Arrays.asList(new LabelAtom(getLabel())));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized Collection<PlannedNode> provision(Label label, int excessWorkload) {
        List<NodeProvisioner.PlannedNode> list = new ArrayList<NodeProvisioner.PlannedNode>();

        // guard against non-matching labels
        if (label != null && !label.matches(Arrays.asList(new LabelAtom(getLabel())))) {
            return list;
        }

        // guard against double-provisioning with a 500ms cooldown clock
        long timeDiff = System.currentTimeMillis() - lastProvisionTime;
        if (timeDiff < 500) {
            LOGGER.info("[AWS Lambda Cloud]: Provision of {} skipped, still on cooldown ({}ms of 500ms)", excessWorkload,
                timeDiff);
            return list;
        }

        String labelName = label == null ? getLabel() : label.getDisplayName();
        long stillProvisioning = numStillProvisioning();
        long numToLaunch = Math.max(excessWorkload - stillProvisioning, 0);
        LOGGER.info("[AWS Lambda Cloud]: Provisioning {} nodes for label '{}' ({} already provisioning)", numToLaunch, labelName,
            stillProvisioning);

        for (int i = 0; i < numToLaunch; i++) {
            final String suffix = RandomStringUtils.randomAlphabetic(6);
            //final String displayName = String.format("%s.cb-%s", functionName, suffix);
            final String displayName = String.format("%s.lambda-%s", functionName, suffix);
            final LambdaCloud cloud = this;
            final Future<Node> nodeResolver = Computer.threadPoolForRemoting.submit(() -> {
                LambdaLauncher launcher = new LambdaLauncher(cloud);
                LambdaNode agent = new LambdaNode(cloud, displayName, launcher);
                Jenkins.getActiveInstance().addNode(agent);
                return agent;
            });
            list.add(new NodeProvisioner.PlannedNode(displayName, nodeResolver, 1));
        }

        lastProvisionTime = System.currentTimeMillis();
        return list;
    }

    /**
    * Find the number of {@link LambdaNode} instances still connecting to
    * Jenkins host.
    */
    private long numStillProvisioning() {
        return Jenkins.getActiveInstance().getNodes().stream().filter(LambdaNode.class::isInstance).map(LambdaNode.class::cast)
            .filter(a -> a.getLauncher().isLaunchSupported()).count();
    }

    @Extension
    public static class LambdaCloudDescriptorImpl extends LambdaCloudDescriptor{};

}
