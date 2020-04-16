package org.jlamande.jenkins.plugins.aws.lambda.cloud;

import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.LogType;

import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.Base64;

import javax.annotation.Nonnull;

import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LambdaLauncher class.
 *
 * @author jlamande
 */
public class LambdaLauncher extends JNLPLauncher {

    private static final int sleepMs = 500;

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaLauncher.class);

    private final LambdaCloud cloud;

    private boolean launched;

    /**
    * Constructor for LambdaLauncher.
    *
    * @param cloud a {@link LambdaCloud} object.
    */
    public LambdaLauncher(LambdaCloud cloud) {
        super();
        this.cloud = cloud;
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

        LOGGER.info("[AWS Lambda Cloud]: Launching {} with {}", computer, listener);
        // LambdaComputer cbcpu = (LambdaComputer) computer;
        InvokeRequest request = new InvokeRequest()
            .withFunctionName(cloud.getFunctionName())
            .withPayload(payload(computer))
            .withLogType(LogType.Tail);

        try {
            InvokeResult result = cloud.getClient().invoke(request);
            LOGGER.info("[AWS Lambda Cloud]: Invocation status: {}, result: {}", result.getStatusCode(), getLogResult(result));
            String functionError = result.getFunctionError();
            if (functionError != null) {
                throw new RuntimeException("[AWS Lambda Cloud] : Invoke lambda failed! " + this.getPayloadAsString(result));
            }
            // return this.getPayloadAsString(result);

            LOGGER.info("[AWS Lambda Cloud]: Waiting for agent '{}' to connect ...", computer);
            for (int i = 0; i < cloud.getAgentTimeout() * (1000 / sleepMs); i++) {
                if (computer.isOnline() && computer.isAcceptingTasks()) {
                    LOGGER.info("[AWS Lambda Cloud]: Agent '{}' connected.", computer);
                    launched = true;
                    return;
                }
                Thread.sleep(sleepMs);
            }
            throw new TimeoutException("Timed out while waiting for agent " + node);
        } catch (Exception e) {
            LOGGER.error("[AWS Lambda Cloud]: Exception while starting build: {}", e.getMessage(), e);
            listener.fatalError("Exception while starting build: %s", e.getMessage());

            if (node instanceof LambdaNode) {
                try {
                    Jenkins.getActiveInstance().removeNode(node);
                } catch (IOException e1) {
                    LOGGER.error("Failed to terminate agent: {}", node.getDisplayName(), e);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void beforeDisconnect(@Nonnull SlaveComputer computer, @Nonnull TaskListener listener) {
        if (computer instanceof LambdaComputer) {
            // ((LambdaComputer) computer).setBuildId(null);
        }
    }

    private String payload(@Nonnull SlaveComputer computer) {
        Node n = computer.getNode();
        // TODO: use an object and JSON serialization
        String payload = String.format("{\"url\": \"%s\", \"node_secret\": \"%s\", \"node_name\": \"%s\"}", cloud.getJenkinsUrl(), computer.getJnlpMac(), n.getDisplayName());
        return payload;
    }

    private String getPayloadAsString(InvokeResult result) {
        return new String(result.getPayload().array(), StandardCharsets.UTF_8);
    }

    private String getLogResult(InvokeResult result) {
        return new String(Base64.getDecoder().decode(result.getLogResult()), StandardCharsets.UTF_8);
    }
}
