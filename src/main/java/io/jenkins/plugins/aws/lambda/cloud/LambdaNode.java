package io.jenkins.plugins.aws.lambda.cloud;

import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.ComputerLauncher;

import java.io.IOException;
import java.util.Collections;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * LambdaNode class
 * https://javadoc.jenkins.io/hudson/slaves/AbstractCloudSlave.html
 *
 * @author jlamande
 */
class LambdaNode extends AbstractCloudSlave {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaNode.class);

    private static final long serialVersionUID = -6722929807051421839L;

    private final transient LambdaCloud cloud;

    /**
     * Creates a new LambdaNode node that provisions a
     * {@link LambdaComputer}.
     *
     * @param cloud    a {@link LambdaCloud} object.
     * @param name     the label of the agent.
     * @param name     the name of the agent.
     * @param launcher a {@link hudson.slaves.ComputerLauncher} object.
     * @throws hudson.model.Descriptor.FormException if any.
     * @throws java.io.IOException                   if any.
     */
    public LambdaNode(@Nonnull LambdaCloud cloud, @Nonnull String label, @Nonnull String name, @Nonnull ComputerLauncher launcher)
        throws Descriptor.FormException, IOException {
        // TODO : review
        // - mode : Mode.NORMAL, Mode.EXCLUSIVE
        // - timeout
        // https://javadoc.jenkins.io/hudson/slaves/CloudSlaveRetentionStrategy.html
        super(name, "AWS Lambda Agent", "/tmp", 1, Mode.NORMAL, label, launcher,
            new CloudRetentionStrategy(cloud.getAgentTimeout() / 60), Collections.emptyList());
        this.cloud = cloud;
    }

    /**
     * Get the cloud instance associated with this agent
     *
     * @return a {@link LambdaCloud} object.
     */
    public LambdaCloud getCloud() {
        return cloud;
    }

    /** {@inheritDoc} */
    @Override
    public AbstractCloudComputer<LambdaNode> createComputer() {
        return new LambdaComputer(this);
    }

    /** {@inheritDoc} */
    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().println("[AWS Lambda Cloud]: Terminating node: " + getDisplayName());
        LOGGER.info("[AWS Lambda Cloud]: Terminating node: {}", getDisplayName());
    }
}
