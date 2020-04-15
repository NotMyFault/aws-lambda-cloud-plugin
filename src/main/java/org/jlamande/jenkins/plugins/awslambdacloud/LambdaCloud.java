package org.jlamande.jenkins.plugins.awslambdacloud;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.SdkClientException;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.ListFunctionsRequest;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;

import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

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
    public LambdaCloud(String name, @Nonnull String functionName, @Nullable String credentialsId,
        @Nonnull String region) throws InterruptedException {
        super(StringUtils.isNotBlank(name) ? name : "lambda_" + jenkins().clouds.size());

        this.functionName = functionName;
        this.credentialsId = StringUtils.defaultIfBlank(credentialsId, "");
        if (StringUtils.isBlank(region)) {
            this.region = getDefaultRegion();
        } else {
            this.region = region;
        }
        LOGGER.info("[AWS Lambda Cloud]: Initializing Cloud: {}", this);
    }

    /**
    * The active Jenkins instance.
    *
    * @return a {@link jenkins.model.Jenkins} object.
    */
    @Nonnull
    protected static Jenkins jenkins() {
        return Jenkins.getActiveInstance();
    }

    /**
    * Clear all CodeBuilder nodes on boot-up because these cannot be permanent.
    */
    private static void clearAllNodes() {
        List<Node> nodes = jenkins().getNodes();
        if (nodes.size() == 0) {
            return;
        }

        LOGGER.info("[AWS Lambda Cloud]: Clearing all previous Lambda nodes...");
        for (final Node n : nodes) {
            if (n instanceof LambdaAgent) {
                try {
                    ((LambdaAgent) n).terminate();
                } catch (InterruptedException | IOException e) {
                    LOGGER.error("[AWS Lambda Cloud]: Failed to terminate agent '{}'", n.getDisplayName(), e);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("%s<%s>", name, functionName);
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

    @CheckForNull
    private static AmazonWebServicesCredentials getCredentials(@Nullable String credentialsId) {
        return AWSCredentialsHelper.getCredentials(credentialsId, jenkins());
    }

    private static AWSLambda buildClient(String credentialsId, String region) {
        ProxyConfiguration proxy = jenkins().proxy;
        ClientConfiguration clientConfiguration = new ClientConfiguration();

        if (proxy != null) {
            clientConfiguration.setProxyHost(proxy.name);
            clientConfiguration.setProxyPort(proxy.port);
            clientConfiguration.setProxyUsername(proxy.getUserName());
            clientConfiguration.setProxyPassword(proxy.getPassword());
        }

        AWSLambdaClientBuilder builder = AWSLambdaClientBuilder.standard()
            .withClientConfiguration(clientConfiguration).withRegion(region);

        AmazonWebServicesCredentials credentials = getCredentials(credentialsId);
        if (credentials != null) {
            String awsAccessKeyId = credentials.getCredentials().getAWSAccessKeyId();
            String obfuscatedAccessKeyId = StringUtils.left(awsAccessKeyId, 4)
                + StringUtils.repeat("*", awsAccessKeyId.length() - 8) + StringUtils.right(awsAccessKeyId, 4);
            LOGGER.debug("[AWS Lambda Cloud]: Using credentials: {}", obfuscatedAccessKeyId);
            builder.withCredentials(credentials);
        }
        LOGGER.debug("[AWS Lambda Cloud]: Selected Region: {}", region);

        return builder.build();
    }

    /**
    * Getter for the field <code>client</code>.
    *
    * @return a {@link com.amazonaws.services.codebuild.AWSCodeBuild} object.
    */
    public synchronized AWSLambda getClient() {
        if (this.client == null) {
            this.client = LambdaCloud.buildClient(credentialsId, region);
        }
        return this.client;
    }

    private transient long lastProvisionTime = 0;

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
            final String suffix = RandomStringUtils.randomAlphabetic(4);
            //final String displayName = String.format("%s.cb-%s", functionName, suffix);
            final String displayName = String.format("%s.lambda-%s", functionName, suffix);
            final LambdaCloud cloud = this;
            final Future<Node> nodeResolver = Computer.threadPoolForRemoting.submit(() -> {
                LambdaLauncher launcher = new LambdaLauncher(cloud);
                LambdaAgent agent = new LambdaAgent(cloud, displayName, launcher);
                jenkins().addNode(agent);
                return agent;
            });
            list.add(new NodeProvisioner.PlannedNode(displayName, nodeResolver, 1));
        }

        lastProvisionTime = System.currentTimeMillis();
        return list;
    }

    /**
    * Find the number of {@link LambdaAgent} instances still connecting to
    * Jenkins host.
    */
    private long numStillProvisioning() {
        return jenkins().getNodes().stream().filter(LambdaAgent.class::isInstance).map(LambdaAgent.class::cast)
            .filter(a -> a.getLauncher().isLaunchSupported()).count();
    }

    /** {@inheritDoc} */
    @Override
    public boolean canProvision(Label label) {
        boolean canProvision = label == null ? true : label.matches(Arrays.asList(new LabelAtom(getLabel())));
        // LOGGER.info("[AWS Lambda Cloud]: Check provisioning capabilities for label '{}': {}", label, canProvision);
        return canProvision;
    }

    private static String getDefaultRegion() {
        try {
            return new DefaultAwsRegionProviderChain().getRegion();
        } catch (SdkClientException exc) {
            return DEFAULT_REGION;
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return Messages.displayName();
        }

        public int getDefaultAgentTimeout() {
            return DEFAULT_AGENT_TIMEOUT;
        }

        public ListBoxModel doFillCredentialsIdItems() {
            return AWSCredentialsHelper.doFillCredentialsIdItems(jenkins());
        }

        public ListBoxModel doFillRegionItems() {
            final ListBoxModel options = new ListBoxModel();

            String defaultRegion = getDefaultRegion();
            if (StringUtils.isNotBlank(defaultRegion)) {
                options.add(defaultRegion);
            }

            for (Region r : RegionUtils.getRegionsForService(AWSLambda.ENDPOINT_PREFIX)) {
                if (StringUtils.equals(r.getName(), defaultRegion)) {
                    continue;
                }
                options.add(r.getName());
            }
            return options;
        }

        public ListBoxModel doFillFunctionNameItems(@QueryParameter String credentialsId, @QueryParameter String region) {
            if (StringUtils.isBlank(region)) {
                region = getDefaultRegion();
                if (StringUtils.isBlank(region)) {
                    return new ListBoxModel();
                }
            }

            try {
                final List<String> functions = new ArrayList<String>();
                String lastToken = null;
                do {
                    ListFunctionsResult result = LambdaCloud.buildClient(credentialsId, region)
                        .listFunctions(new ListFunctionsRequest().withMarker(lastToken));
                    //functions.addAll(result.getFunctions().stream().map(f -> f.getFunctionArn()).collect(Collectors.toList()));
                    functions.addAll(result.getFunctions().stream().map(f -> f.getFunctionName()).collect(Collectors.toList()));
                    lastToken = result.getNextMarker();
                } while (lastToken != null);
                Collections.sort(functions);
                final ListBoxModel options = new ListBoxModel();
                for (final String arn : functions) {
                    options.add(arn);
                }
                return options;
            } catch (RuntimeException e) {
                // missing credentials will throw an "AmazonClientException: Unable to load AWS
                // credentials from any provider in the chain"
                LOGGER.error("[AWS Lambda Cloud]: Exception listing functions (region={})", region, e);
                return new ListBoxModel();
            }
        }

        public String getDefaultRegion() {
            return LambdaCloud.getDefaultRegion();
        }
    }
}
