package io.jenkins.plugins.aws.lambda.cloud;

import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.LogType;

import com.google.common.base.Throwables;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import javax.annotation.Nonnull;

import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LambdaComputerLauncher class.
 *
 * @author jlamande
 */
public class LambdaComputerLauncher extends JNLPLauncher {

    private static final int sleepMs = 500;

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaComputerLauncher.class);

    private final LambdaCloud cloud;

    private final LambdaFunction function;

    private boolean launched;

    /**
    * Constructor for LambdaComputerLauncher.
    *
    * @param cloud a {@link LambdaCloud} object.
    */
    public LambdaComputerLauncher(LambdaCloud cloud, LambdaFunction function) {
        super();
        this.cloud = cloud;
        this.function = function;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isLaunchSupported() {
        return !launched;
    }

    /** {@inheritDoc} */
    @Override
    public void launch(@Nonnull SlaveComputer computer, @Nonnull TaskListener listener) {
        this.launched = false;
        if (!(computer instanceof LambdaComputer)) {
            LOGGER.error("[AWS Lambda Cloud]: Not launching {} since it is not the correct type ({})", computer,
            LambdaComputer.class.getName());
            return;
        }

        Node node = computer.getNode();
        if (node == null) {
            LOGGER.error("[AWS Lambda Cloud]: Not launching {} since it is missing a node.", computer);
            return;
        }

        LOGGER.debug("[AWS Lambda Cloud]: Is computer accepting tasks ? {}", computer.isAcceptingTasks());
        LOGGER.debug("[AWS Lambda Cloud]: Is node accepting tasks ? {}", node.isAcceptingTasks());
        LOGGER.debug("[AWS Lambda Cloud]: already launched ? {}", launched);

        if (launched) {
            LOGGER.info("[{}]: Agent has already been launched, activating", node.getNodeName());
            computer.setAcceptingTasks(true);
            return;
        }

        LOGGER.info("[AWS Lambda Cloud]: Launching {} with {}", computer, listener);
        // LambdaComputer cbcpu = (LambdaComputer) computer;
        InvokeRequest request = new InvokeRequest()
            .withFunctionName(function.getFunctionName())
            .withPayload(buildPayload(computer))
            .withLogType(LogType.Tail)
            .withInvocationType(InvocationType.Event);

        try {
            InvokeResult result = cloud.getClient().invoke(request);
            LOGGER.debug("[AWS Lambda Cloud]: Launcher - Invocation status: {}", result.getStatusCode());
            // status codes
            // 200 : successful synchronous invocation
            // 202 : successful asynchronous invocation
            // 204 : successful dry run invocation
            if (result.getFunctionError() != null) {
                throw new RuntimeException("[AWS Lambda Cloud] : Invoke lambda failed ! " + this.getPayloadAsString(result));
            }
            long timeout = System.currentTimeMillis() + Duration.ofSeconds(cloud.getAgentTimeout()).toMillis();
            // now wait for agent to be online
            while (System.currentTimeMillis() < timeout) {
                SlaveComputer agentComputer = ((Slave) node).getComputer();
                if (agentComputer == null) {
                    throw new IllegalStateException("Node was deleted, computer is null");
                }
                if (agentComputer.isOnline()) {
                    break;
                }
                LOGGER.debug("[{}]: Waiting for node to connect", node.getNodeName());
                Thread.sleep(1000);
            }
            SlaveComputer agentComputer = ((Slave) node).getComputer();
            if (agentComputer == null) {
                throw new IllegalStateException("Node was deleted, computer is null");
            }

            if (!agentComputer.isOnline()) {
                throw new IllegalStateException("Node is not connected");
            }

            LOGGER.info("[{}]: Node connected", node.getNodeName());
            computer.setAcceptingTasks(true);
            launched = true;
            try {
                // We need to persist the "launched" setting...
                node.save();
            } catch (IOException e) {
                LOGGER.warn("Could not save() agent: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            LOGGER.error("[AWS Lambda Cloud]: Exception while starting : {}", e.getMessage(), e);
            listener.fatalError("Exception while starting : %s", e.getMessage());

            if (node instanceof LambdaNode) {
                try {
                    Jenkins.getActiveInstance().removeNode(node);
                } catch (IOException e1) {
                    LOGGER.error("Failed to terminate node: {}", node.getDisplayName(), e1);
                }
            }
            throw Throwables.propagate(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void beforeDisconnect(@Nonnull SlaveComputer computer, @Nonnull TaskListener listener) {
        if (computer instanceof LambdaComputer) {
            Node node = computer.getNode();
            if(node != null) {
                LOGGER.error("Before disconnecting node: {}", node.getDisplayName());
                // ((LambdaComputer) computer).setBuildId(null);
            }
        }
    }

    private String buildPayload(@Nonnull SlaveComputer computer) {
        String displayName = "";
        Node node = computer.getNode();
        if(node != null) {
            displayName = node.getDisplayName();
        }
        // TODO: use an object and JSON serialization
        String payload = String.format("{\"url\": \"%s\", \"node_secret\": \"%s\", \"node_name\": \"%s\"}", cloud.getJenkinsUrl(), computer.getJnlpMac(), displayName);
        return payload;
    }

    private String getPayloadAsString(InvokeResult result) {
        return new String(result.getPayload().array(), StandardCharsets.UTF_8);
    }

    private String getLogResult(InvokeResult result) {
        return new String(Base64.getDecoder().decode(result.getLogResult()), StandardCharsets.UTF_8);
    }
}
