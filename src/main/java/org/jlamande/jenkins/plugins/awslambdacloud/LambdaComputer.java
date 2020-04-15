package org.jlamande.jenkins.plugins.awslambdacloud;

import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LambdaComputer class.
 *
 * @author jlamande
 */
public class LambdaComputer extends AbstractCloudComputer<LambdaAgent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaComputer.class);

    private String buildId;

    @Nonnull
    private final LambdaCloud cloud;

    /**
    * Constructor for LambdaComputer.
    *
    * @param agent a {@link LambdaAgent} object.
    */
    public LambdaComputer(LambdaAgent agent) {
        super(agent);
        this.cloud = agent.getCloud();
    }

    /** {@inheritDoc} */
    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);
        LOGGER.info("[AWS Lambda Cloud]: [{}]: Task in job '{}' accepted", this, task.getFullDisplayName());
    }

    /** {@inheritDoc} */
    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        super.taskCompleted(executor, task, durationMS);
        LOGGER.info("[AWS Lambda Cloud]: [{}]: Task in job '{}' completed in {}ms", this, task.getFullDisplayName(), durationMS);
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

        Future<Object> next = Computer.threadPoolForRemoting.submit(() -> {
            LOGGER.info("[AWS Lambda Cloud]: [{}]: Terminating agent after task.", this);
            try {
                Thread.sleep(500);
                LambdaCloud.jenkins().removeNode(getNode());
            } catch (Exception e) {
                LOGGER.info("[AWS Lambda Cloud]: [{}]: Termination error: {}", this, e.getClass());
            }
            return null;
        });
        try {
            next.notify();
        } catch (java.lang.IllegalMonitorStateException e) {
            LOGGER.warn("exception during shutdown");
        }
    }
}
