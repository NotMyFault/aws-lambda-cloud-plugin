package io.jenkins.plugins.aws.lambda.cloud;

import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;

import javax.annotation.Nonnull;

import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LambdaComputer class.
 *
 * @author jlamande
 */
public class LambdaComputer extends AbstractCloudComputer<LambdaNode> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaComputer.class);

    @Nonnull
    private final LambdaCloud cloud;

    /**
    * Constructor for LambdaComputer.
    *
    * @param node a {@link LambdaNode} object.
    */
    public LambdaComputer(LambdaNode node) {
        super(node);
        this.cloud = node.getCloud();
    }

    /** {@inheritDoc} */
    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);
        LOGGER.info("[AWS Lambda Cloud]: [{}]: Task in job '{}' accepted", this, task.getFullDisplayName());
        LOGGER.debug("[AWS Lambda Cloud]: [{}] -  online : {} - isAcceptingTasks : {}", this, this.isOnline(), this.isAcceptingTasks());
    }

    /** {@inheritDoc} */
    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        super.taskCompleted(executor, task, durationMS);
        LOGGER.debug("[AWS Lambda Cloud]: [{}]: Task in job '{}' completed in {}ms", this, task.getFullDisplayName(), durationMS);
        gracefulShutdown();
    }

    /** {@inheritDoc} */
    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
        LOGGER.error("[AWS Lambda Cloud]: [{}]: Task in job '{}' completed with problems in {}ms", this,
            task.getFullDisplayName(), durationMS, problems);
        gracefulShutdown();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("name: %s", getName());
    }

    private void gracefulShutdown() {
        setAcceptingTasks(false);

        //Future<Object> next =
        //Computer.threadPoolForRemoting.submit(() -> {
            LOGGER.info("[AWS Lambda Cloud]: [{}]: Terminating agent after task.", this);
            try {
                // Thread.sleep(500);
                Node node = getNode();
                if(node != null) {
                    Jenkins.getActiveInstance().removeNode(node);
                }
            } catch (Exception e) {
                LOGGER.warn("[AWS Lambda Cloud]: [{}]: Termination error: {}", this, e.getClass());
            }
            //return null;
        //});
        //try {
        //    next.notify();
        //} catch (java.lang.IllegalMonitorStateException e) {
        //    LOGGER.warn("exception during shutdown ", e);
        //}
    }
}
