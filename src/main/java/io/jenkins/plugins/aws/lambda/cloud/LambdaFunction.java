package io.jenkins.plugins.aws.lambda.cloud;

import com.amazonaws.services.lambda.model.ListFunctionsRequest;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.jenkins.plugins.aws.lambda.cloud.LambdaCloud.getDefaultRegion;

/**
 * LambdaFunction Describable class.
 *
 * @author jlamande
 */
public class LambdaFunction extends AbstractDescribableImpl<LambdaFunction> implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaCloud.class);

    /**
     * Function Name (same account)
     */
    @Nonnull
    private final String functionName;

    /**
     * White-space separated list of {@link hudson.model.Node} labels.
     *
     * @see hudson.model.Label
     */
    @CheckForNull
    private final String label;

    /**
     *
     * @param functionName   the name of the AWS Lambda function to build from.
     * @param label the label used to identify this agent(node) in Jenkins.
     */
    @DataBoundConstructor
    public LambdaFunction(@Nonnull String functionName,
                           @Nullable String label) {
        this.functionName = functionName;
        this.label = label;
    }

    public String getFunctionName() {return functionName; }

    /**
     * Getter for the field <code>label</code>.
     *
     * @return a {@link String} object.
     */
    @Nonnull
    public String getLabel() {
        return StringUtils.defaultIfBlank(label, "");
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<LambdaFunction> {

        @Override
        public String getDisplayName() {
            return Messages.function();
        }

        public ListBoxModel doFillFunctionNameItems(@QueryParameter @RelativePath("..") String credentialsId, @QueryParameter @RelativePath("..") String region) {
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
                    ListFunctionsResult result = LambdaClient.buildClient(credentialsId, region)
                        .listFunctions(new ListFunctionsRequest().withMarker(lastToken));
                    //functions.addAll(result.getFunctions().stream().map(f -> f.getFunctionArn()).collect(Collectors.toList()));
                    functions.addAll(result.getFunctions().stream().map(f -> f.getFunctionName()).collect(Collectors.toList()));
                    lastToken = result.getNextMarker();
                } while (lastToken != null);
                Collections.sort(functions);
                final StandardListBoxModel options = new StandardListBoxModel();
                options.includeEmptyValue();
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
    }
}
