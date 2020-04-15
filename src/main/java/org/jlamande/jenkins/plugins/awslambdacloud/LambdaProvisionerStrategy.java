package org.jlamande.jenkins.plugins.awslambdacloud;

import hudson.Extension;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import hudson.slaves.Cloud;
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
 * Based on https://github.com/jenkinsci/one-shot-executor-plugin/blob/master/src/main/java/org/jenkinsci/plugins/oneshot/OneShotProvisionerStrategy.java
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class LambdaProvisionerStrategy extends Strategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaProvisionerStrategy.class);

    @Nonnull
    @Override
    public StrategyDecision apply(@Nonnull NodeProvisioner.StrategyState state) {
        if (Jenkins.getActiveInstance().isQuietingDown()) {
            return CONSULT_REMAINING_STRATEGIES;
        }

        for (Cloud cloud : Jenkins.getActiveInstance().clouds) {
            if (cloud instanceof LambdaCloud) {
                final StrategyDecision decision = applyForCloud(state, (LambdaCloud) cloud);
                if (decision == PROVISIONING_COMPLETED) return decision;
            }
        }
        return CONSULT_REMAINING_STRATEGIES;
    }

    private StrategyDecision applyForCloud(@Nonnull NodeProvisioner.StrategyState state, LambdaCloud cloud) {

        final Label label = state.getLabel();

        if (!cloud.canProvision(label)) {
            return CONSULT_REMAINING_STRATEGIES;
        }

        LoadStatistics.LoadStatisticsSnapshot snapshot = state.getSnapshot();
        LOGGER.info("Available executors={}, connecting={}, planned={}", ""+snapshot.getAvailableExecutors(), ""+snapshot.getConnectingExecutors(), ""+state.getPlannedCapacitySnapshot());
        int availableCapacity =
              snapshot.getAvailableExecutors()
            + snapshot.getConnectingExecutors()
            + state.getPlannedCapacitySnapshot();

        int currentDemand = snapshot.getQueueLength();
        LOGGER.debug("Available capacity={0}, currentDemand={1}", ""+availableCapacity, ""+currentDemand);

        if (availableCapacity < currentDemand) {
            Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(label, currentDemand - availableCapacity);
            LOGGER.debug("Planned {0} new nodes", ""+plannedNodes.size());
            state.recordPendingLaunches(plannedNodes);
            availableCapacity += plannedNodes.size();
            LOGGER.debug("After provisioning, available capacity={0}, currentDemand={1}", ""+availableCapacity, ""+currentDemand);
        }

        if (availableCapacity >= currentDemand) {
            LOGGER.info("Provisioning completed");
            return PROVISIONING_COMPLETED;
        } else {
            LOGGER.info("Provisioning not complete, consulting remaining strategies");
            return CONSULT_REMAINING_STRATEGIES;
        }
    }

    /**
     * Ping the nodeProvisioner as a new task enters the queue, so it can provision a LambdaAgent without delay.
     */
    @Extension
    public static class LambdaProvisionning extends QueueListener {

        @Override
        public void onEnterBuildable(Queue.BuildableItem item) {
            final Jenkins jenkins = Jenkins.getActiveInstance();
            final Label label = item.getAssignedLabel();
            for (Cloud cloud : jenkins.clouds) {
                if (cloud instanceof LambdaCloud && cloud.canProvision(label)) {
                    final NodeProvisioner provisioner = (label == null
                            ? jenkins.unlabeledNodeProvisioner
                            : label.nodeProvisioner);
                    provisioner.suggestReviewNow();
                }
            }
        }
    }
}
