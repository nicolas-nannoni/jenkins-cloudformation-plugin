package com.syncapse.jenkinsci.plugins.awscloudformationwrapper;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 *
 * @author erickdovale
 *
 */
public class StackBean extends AbstractDescribableImpl<StackBean> {

    /**
     * The name of the stack.
     */
    private final String stackName;

    /**
     * The string to prepend to the outputs of the stack being created.
     */
    private final String outputPrefixName;

    /**
     * The description of the cloud formation stack that will be launched.
     */
    private final String description;

    /**
     * The json file with the Cloud Formation definition.
     */
    private final String cloudFormationRecipe;

    /**
     * The parameters to be passed into the cloud formation.
     */
    private final String parameters;

    /**
     * Time to wait for a stack to be created before giving up and failing the build.
     */
    private final long timeout;

    /**
     * The access key to call Amazon's APIs
     */
    private final String awsAccessKey;

    /**
     * The secret key to call Amazon's APIs
     */
    private final String awsSecretKey;

    /**
     * Whether or not the stack should be deleted automatically when the job completes
     */
    private boolean autoDeleteStack = true;

    private final String awsRegion;

    @DataBoundConstructor
    public StackBean(final String stackName, final String outputPrefixName, final String description, final String cloudFormationRecipe,
            final String parameters, final long timeout, final String awsAccessKey, final String awsSecretKey, final boolean autoDeleteStack,
            final String awsRegion) {

        super();
        this.stackName = stackName;
        this.outputPrefixName = outputPrefixName;
        this.description = description;
        this.cloudFormationRecipe = cloudFormationRecipe;
        this.parameters = parameters;
        this.timeout = timeout;
        this.awsAccessKey = awsAccessKey;
        this.awsSecretKey = awsSecretKey;
        this.autoDeleteStack = autoDeleteStack;
        this.awsRegion = awsRegion;
    }

    public String getStackName() {

        return stackName;
    }

    public String getOutputPrefixName() {

        return outputPrefixName;
    }

    public String getDescription() {

        return description;
    }

    public String getCloudFormationRecipe() {

        return cloudFormationRecipe;
    }

    public String getParameters() {

        return parameters;
    }

    public long getTimeout() {

        return timeout;
    }

    public String getAwsAccessKey() {

        return awsAccessKey;
    }

    public String getAwsSecretKey() {

        return awsSecretKey;
    }

    public boolean getAutoDeleteStack() {

        return autoDeleteStack;
    }

    public String getAwsRegion() {

        return awsRegion;
    }

    public Region getParsedAwsRegion(final EnvVars env) {

        final String regionName = getParsedValue(env, awsRegion);
        return Region.getFromShortName(regionName);
    }

    public Map<String, String> getParsedParameters(final EnvVars env) {

        if (parameters == null || parameters.isEmpty())
            return new HashMap<String, String>();

        final Map<String, String> result = new HashMap<String, String>();
        String token[] = null;

        // semicolon delimited list
        if (parameters.contains(";")) {
            for (final String param : parameters.split(";")) {
                token = param.split("=");
                result.put(token[0].trim(), env.expand(token[1].trim()));
            }
        } else {
            // comma delimited parameter list
            for (final String param : parameters.split(",")) {
                token = param.split("=");
                result.put(token[0].trim(), env.expand(token[1].trim()));
            }
        }
        return result;
    }

    public String getParsedValue(final EnvVars env, final String value) {

        return env.expand(value);
    }

    public String getParsedAwsAccessKey(final EnvVars env) {

        return env.expand(getAwsAccessKey());
    }

    public String getParsedAwsSecretKey(final EnvVars env) {

        return env.expand(getAwsSecretKey());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<StackBean> {

        @Override
        public String getDisplayName() {

            return "Cloud Formation";
        }

        public FormValidation doCheckStackName(@AncestorInPath final AbstractProject<?, ?> project, @QueryParameter final String value) throws IOException {

            if (0 == value.length()) {
                return FormValidation.error("Empty stack name");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckTimeout(@AncestorInPath final AbstractProject<?, ?> project, @QueryParameter final String value) throws IOException {

            if (value.length() > 0) {
                try {
                    Long.parseLong(value);
                } catch (final NumberFormatException e) {
                    return FormValidation.error("Timeout value " + value + " is not a number.");
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckCloudFormationRecipe(@AncestorInPath final AbstractProject<?, ?> project, @QueryParameter final String value)
                throws IOException {

            if (0 == value.length()) {
                return FormValidation.error("Empty recipe file.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckAwsAccessKey(@AncestorInPath final AbstractProject<?, ?> project, @QueryParameter final String value) throws IOException {

            if (0 == value.length()) {
                return FormValidation.error("Empty aws access key");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckAwsSecretKey(@AncestorInPath final AbstractProject<?, ?> project, @QueryParameter final String value) throws IOException {

            if (0 == value.length()) {
                return FormValidation.error("Empty aws secret key");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillAwsRegionItems() {

            final ListBoxModel items = new ListBoxModel();
            for (final Region region : Region.values()) {
                items.add(region.readableName, region.name());
            }
            return items;
        }

    }

}
