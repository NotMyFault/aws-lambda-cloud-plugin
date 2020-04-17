package org.jlamande.jenkins.plugins.aws.lambda.cloud;

import hudson.Extension;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueListener;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;

import java.util.Collection;

import javax.annotation.Nonnull;

import jenkins.model.Jenkins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static hudson.slaves.NodeProvisioner.Strategy;
import static hudson.slaves.NodeProvisioner.StrategyDecision;
import static hudson.slaves.NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
import static hudson.slaves.NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;

/**
 * Based on :
 * https://github.com/jenkinsci/amazon-ecs-plugin/blob/master/src/main/java/com/cloudbees/jenkins/plugins/amazonecs/ECSProvisioningStrategy.java
 * https://github.com/jenkinsci/one-shot-executor-plugin/blob/master/src/main/java/org/jenkinsci/plugins/oneshot/OneShotProvisionerStrategy.java
 *
 * @author jlamande
 */
@Extension(ordinal = 50)
public class LambdaCloudProvisionerStrategy extends Strategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaCloudProvisionerStrategy.class);

    private static final boolean DISABLED_STRATEGY = Boolean.valueOf(
        System.getProperty("org.jlamande.jenkins.plugins.aws.lambda.cloud.LambdaCloudProvisionerStrategy.disable"));

    /**
     * Takes a provisioning decision for a single label. Determines how many ECS tasks to start based solely on
     * queue length and how many agents are in the process of connecting.
     */
    @Nonnull
    @Override
    public StrategyDecision apply(@Nonnull NodeProvisioner.StrategyState state) {
        LOGGER.debug( "[AWS Lambda Cloud]: LambdaCloudProvisionerStrategy received {}", state);
        if (DISABLED_STRATEGY) {
            LOGGER.info("Provisioning not complete, LambdaCloudProvisionerStrategy is disabled");
            return CONSULT_REMAINING_STRATEGIES;
        }

        LoadStatistics.LoadStatisticsSnapshot snap = state.getSnapshot();
        Label label = state.getLabel();

        int excessWorkload = snap.getQueueLength() - snap.getAvailableExecutors() - snap.getConnectingExecutors();

        CLOUD:
        for (Cloud c : Jenkins.getActiveInstance().clouds) {
            if (excessWorkload <= 0) {
                break;  // enough agents allocated
            }

            // Make sure this cloud actually can provision for this label.
            if (!c.canProvision(label)) {
                continue;
            }

            for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
                CauseOfBlockage causeOfBlockage = cl.canProvision(c, label, excessWorkload);
                if (causeOfBlockage != null) {
                    continue CLOUD;
                }
            }

            Collection<NodeProvisioner.PlannedNode> additionalCapacities = c.provision(label, excessWorkload);

            // compat with what the default NodeProvisioner.Strategy does
            fireOnStarted(c, label, additionalCapacities);

            for (NodeProvisioner.PlannedNode ac : additionalCapacities) {
                excessWorkload -= ac.numExecutors;
                LOGGER.debug( "Started provisioning {} from {} with {} "
                        + "executors. Remaining excess workload: {}",
                    ac.displayName, c.name, ac.numExecutors, excessWorkload);
            }
            state.recordPendingLaunches(additionalCapacities);
        }
        // we took action, only pass on to other strategies if our action was insufficient
        return excessWorkload > 0 ? CONSULT_REMAINING_STRATEGIES : PROVISIONING_COMPLETED;
    }

    private static void fireOnStarted(final Cloud cloud, final Label label,
                                      final Collection<NodeProvisioner.PlannedNode> plannedNodes) {
        for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
            try {
                cl.onStarted(cloud, label, plannedNodes);
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                LOGGER.error("Unexpected uncaught exception encountered while "
                    + "processing onStarted() listener call in " + cl + " for label "
                    + label.toString(), e);
            }
        }
    }

    /**
     * Ping the nodeProvisioner as a new task enters the queue, so it can provision a LambdaNode without delay.
     *
     *//*
     // be careful when using it as it may result in multiple agents being launched - currently in progress */
    @Extension
    public static class LambdaProvisioningQueueListener extends QueueListener {

        private static final Logger LOGGER = LoggerFactory.getLogger(LambdaProvisioningQueueListener.class);

        @Override
        public void onEnterBuildable(Queue.BuildableItem item) {
            LOGGER.debug("LambdaProvisioningQueueListener - onEnterBuildable");
            final Jenkins jenkins = Jenkins.getActiveInstance();
            final Label label = item.getAssignedLabel();
            for (Cloud cloud : jenkins.clouds) {
                LOGGER.debug("LambdaProvisioningQueueListener - cloud : " + cloud.getDisplayName() + " - label : " + label);
                if (cloud instanceof LambdaCloud && cloud.canProvision(label)) {
                    LOGGER.debug("LambdaProvisioningQueueListener - cloud can provision label " + label);
                    final NodeProvisioner provisioner = (label == null
                            ? jenkins.unlabeledNodeProvisioner
                            : label.nodeProvisioner);
                    LOGGER.debug("LambdaProvisioningQueueListener - provisioner for label {} ", provisioner.toString(), label);
                    provisioner.suggestReviewNow();
                }
            }
        }
    }//*/
}
