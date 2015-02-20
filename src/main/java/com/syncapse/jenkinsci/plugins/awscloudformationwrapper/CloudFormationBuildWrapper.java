/**
 *
 */
package com.syncapse.jenkinsci.plugins.awscloudformationwrapper;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author erickdovale
 *
 */
public class CloudFormationBuildWrapper extends BuildWrapper {

    protected List<StackBean> stacks;

    private transient List<CloudFormation> cloudFormations = new ArrayList<CloudFormation>();

    @DataBoundConstructor
    public CloudFormationBuildWrapper(final List<StackBean> stacks) {

        this.stacks = stacks;
    }

    @Override
    public void makeBuildVariables(final AbstractBuild build, final Map<String, String> variables) {

        for (final CloudFormation cf : cloudFormations) {
            variables.putAll(cf.getOutputs());
        }

    }

    @Override
    public Environment setUp(final AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {

        final EnvVars env = build.getEnvironment(listener);
        env.overrideAll(build.getBuildVariables());

        boolean success = true;

        for (final StackBean stackBean : stacks) {

            final CloudFormation cloudFormation = newCloudFormation(stackBean, build, env, listener.getLogger());

            try {
                if (cloudFormation.create()) {
                    cloudFormation.printStackOutput();
                    cloudFormations.add(cloudFormation);
                    env.putAll(cloudFormation.getOutputs());
                } else {
                    build.setResult(Result.FAILURE);
                    success = false;
                    break;
                }
            } catch (final TimeoutException e) {
                listener.getLogger()
                        .append("ERROR creating stack with name " + stackBean.getStackName()
                                        + ". Operation timedout. Try increasing the timeout period in your stack configuration.");
                build.setResult(Result.FAILURE);
                success = false;
                break;
            }

        }

        // If any stack fails to create then destroy them all
        if (!success) {
            doTearDown();
            return null;
        }

        return new Environment() {

            @Override
            public boolean tearDown(final AbstractBuild build, final BuildListener listener) throws IOException, InterruptedException {

                return doTearDown();

            }

        };
    }

    protected boolean doTearDown() throws IOException, InterruptedException {

        boolean result = true;

        final List<CloudFormation> reverseOrder = new ArrayList<CloudFormation>(cloudFormations);
        Collections.reverse(reverseOrder);

        for (final CloudFormation cf : reverseOrder) {
            // automatically delete the stack?
            if (cf.getAutoDeleteStack()) {
                // delete the stack
                result = result && cf.delete();
            }
        }

        return result;
    }

    protected CloudFormation newCloudFormation(final StackBean stackBean, final AbstractBuild<?, ?> build, final EnvVars env, final PrintStream logger)
            throws IOException {

        return new CloudFormation(logger, stackBean.getStackName(), stackBean.getOutputPrefixName(), build.getWorkspace()
                                                                                                          .child(stackBean.getCloudFormationRecipe())
                                                                                                          .readToString(), stackBean.getParsedParameters(env),
                                  stackBean.getTimeout(), stackBean.getParsedAwsAccessKey(env), stackBean.getParsedAwsSecretKey(env), stackBean.getParsedAwsRegion(env),
                                  stackBean.getAutoDeleteStack(), env, false);

    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public String getDisplayName() {

            return "Create AWS Cloud Formation stack";
        }

        @Override
        public boolean isApplicable(final AbstractProject<?, ?> item) {

            return true;
        }

    }

    public List<StackBean> getStacks() {

        return stacks;
    }

    /**
     * @return
     */
    private Object readResolve() {

        // Initialize the cloud formation collection during deserialization to avoid NPEs.
        cloudFormations = new ArrayList<CloudFormation>();
        return this;
    }

}
