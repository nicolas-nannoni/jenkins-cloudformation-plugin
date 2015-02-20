package com.syncapse.jenkinsci.plugins.awscloudformationwrapper;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 *
 * @author erickdovale
 *
 */
public class SimpleStackBean extends AbstractDescribableImpl<SimpleStackBean> {

    /**
     * The name of the stack.
     */
    private final String stackName;

    /**
     * The string to prepend to the outputs of the stack being created.
     */
    private final String outputPrefixName;

    /**
     * The access key to call Amazon's APIs
     */
    private final String awsAccessKey;

    /**
     * The secret key to call Amazon's APIs
     */
    private final String awsSecretKey;

    /**
     * The AWS Region to work against.
     */
    private final String awsRegion;

    private final Boolean isPrefixSelected;

    @DataBoundConstructor
    public SimpleStackBean(final String stackName, final String outputPrefixName, final String awsAccessKey, final String awsSecretKey, final String awsRegion,
            final Boolean isPrefixSelected) {

        this.stackName = stackName;
        this.outputPrefixName = outputPrefixName;
        this.awsAccessKey = awsAccessKey;
        this.awsSecretKey = awsSecretKey;
        this.awsRegion = awsRegion;
        this.isPrefixSelected = isPrefixSelected;

    }

    public String getStackName() {

        return stackName;
    }

    public String getOutputPrefixName() {

        return outputPrefixName;
    }

    public String getAwsAccessKey() {

        return awsAccessKey;
    }

    public String getAwsSecretKey() {

        return awsSecretKey;
    }

    public Boolean getIsPrefixSelected() {

        return isPrefixSelected;
    }

    public String getAwsRegion() {

        return awsRegion;
    }

    public String getParsedAwsAccessKey(final EnvVars env) {

        return env.expand(getAwsAccessKey());
    }

    public String getParsedAwsSecretKey(final EnvVars env) {

        return env.expand(getAwsSecretKey());
    }

    public String getParsedValue(final EnvVars env, final String value) {

        return env.expand(value);
    }

    public Region getParsedAwsRegion(final EnvVars env) {

        final String regionName = getParsedValue(env, awsRegion);
        return Region.getFromShortName(regionName);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SimpleStackBean> {

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
