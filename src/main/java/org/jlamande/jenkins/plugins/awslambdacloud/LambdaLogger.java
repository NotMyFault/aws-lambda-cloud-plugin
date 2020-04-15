package org.jlamande.jenkins.plugins.awslambdacloud;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.util.Arrays;

import jenkins.tasks.SimpleBuildWrapper;

import org.kohsuke.stapler.DataBoundConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LambdaLogger class.
 *
 * @author jlamande
 */
public final class LambdaLogger extends SimpleBuildWrapper {
    /**
    * Constructor for LambdaLogger.
    */
    @DataBoundConstructor
    public LambdaLogger() {
        super();
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.loggerName();
        }

        @Override
        public boolean isApplicable(final AbstractProject<?, ?> item) {
            return true;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener,
        EnvVars initialEnvironment) throws IOException, InterruptedException {
        Computer cpu = Arrays.asList(LambdaCloud.jenkins().getComputers()).stream()
            .filter(c -> c.getChannel() == launcher.getChannel()).findFirst().get();
        if (cpu instanceof LambdaComputer) {
            LambdaComputer cbCpu = (LambdaComputer) cpu;
            listener.getLogger().println("[AWS Lambda Cloud]: " + Messages.loggerStarted() + ": ");
        }
    }
}
