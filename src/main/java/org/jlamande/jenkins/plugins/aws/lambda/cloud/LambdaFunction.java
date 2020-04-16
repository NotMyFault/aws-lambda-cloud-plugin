package org.jlamande.jenkins.plugins.aws.lambda.cloud;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Set;

public class LambdaFunction extends AbstractDescribableImpl<LambdaFunction> implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaCloud.class);

    /**
     * Function Name or Arn
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

    @DataBoundConstructor
    public LambdaFunction(@Nonnull String functionName,
                           @Nullable String label) {
        this.functionName = functionName;
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    public String getDisplayName() {
        return "Lambda Agent " + label;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<LambdaFunction> {

        private static String TEMPLATE_NAME_PATTERN = "[a-z|A-Z|0-9|_|-]{1,127}";

        @Override
        public String getDisplayName() {
            return Messages.function();
        }

        /*
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
                    ListFunctionsResult result = LambdaCloud.getClient()
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
        }*/
    }
}
