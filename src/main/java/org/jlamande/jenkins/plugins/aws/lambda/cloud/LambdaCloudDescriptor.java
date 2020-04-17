package org.jlamande.jenkins.plugins.aws.lambda.cloud;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.ListFunctionsRequest;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import hudson.model.Descriptor;
import hudson.slaves.Cloud;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LambdaCloudDescriptor extends Descriptor<Cloud> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaCloudDescriptor.class);

    private static String CLOUD_NAME_PATTERN = "[a-z|A-Z|0-9|_|-]{1,127}";

    @Override
    public String getDisplayName() {
        return Messages.displayName();
    }

    public String getDefaultRegion() {
        return LambdaCloud.getDefaultRegion();
    }

    public int getDefaultAgentTimeout() {
        return LambdaCloud.getDefaultAgentTimeout();
    }

    // TODO: fix as reviewed
    public ListBoxModel doFillCredentialsIdItems() {
        return AWSCredentialsHelper.doFillCredentialsIdItems(Jenkins.getActiveInstance());
    }

    public ListBoxModel doFillRegionItems() {
        final ListBoxModel options = new ListBoxModel();

        String defaultRegion = getDefaultRegion();
        if (StringUtils.isNotBlank(defaultRegion)) {
            options.add(defaultRegion);
        }

        for (Region r : RegionUtils.getRegionsForService(AWSLambda.ENDPOINT_PREFIX)) {
            if (StringUtils.equals(r.getName(), defaultRegion)) {
                continue;
            }
            options.add(r.getName());
        }
        return options;
    }

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
                ListFunctionsResult result = LambdaClient.buildClient(credentialsId, region)
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
    }

    public FormValidation doCheckName(@QueryParameter String value) throws IOException, ServletException {
        if (value.length() > 0 && value.length() <= 127 && value.matches(CLOUD_NAME_PATTERN)) {
            return FormValidation.ok();
        }
        return FormValidation.error("Up to 127 letters (uppercase and lowercase), numbers, hyphens, and underscores are allowed");
    }
}
