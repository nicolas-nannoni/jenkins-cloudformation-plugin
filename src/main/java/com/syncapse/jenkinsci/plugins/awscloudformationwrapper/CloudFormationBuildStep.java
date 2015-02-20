/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.syncapse.jenkinsci.plugins.awscloudformationwrapper;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author amit.gilad
 */
public class CloudFormationBuildStep extends Builder {

    private static final Logger LOGGER = Logger.getLogger(CloudFormationBuildStep.class.getName());
    private final List<PostBuildStackBean> stacks;

    @DataBoundConstructor
    public CloudFormationBuildStep(final List<PostBuildStackBean> stacks) {

        this.stacks = stacks;
    }

    public List<PostBuildStackBean> getStacks() {

        return stacks;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {

        return BuildStepMonitor.BUILD;
    }

    @Override
    public boolean prebuild(final AbstractBuild<?, ?> build, final BuildListener listener) {

        LOGGER.info("prebuild");
        return super.prebuild(build, listener);
    }

    @Override
    public Action getProjectAction(final AbstractProject<?, ?> project) {

        LOGGER.info("getProjectAction");
        return super.getProjectAction(project);
    }

    @Override
    public Collection<? extends Action> getProjectActions(final AbstractProject<?, ?> project) {

        LOGGER.info("getProjectActions");
        return super.getProjectActions(project);
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {

        final EnvVars envVars = build.getEnvironment(listener);
        envVars.overrideAll(build.getBuildVariables());

        boolean result = true;

        for (final PostBuildStackBean stack : stacks) {
            final CloudFormation cloudFormation = newCloudFormation(stack, build, envVars, listener.getLogger());
            /*
             * CloudFormation cloudFormation = new CloudFormation( listener.getLogger(), stack.getStackName(), "", new
             * HashMap<String, String>(), 0, stack.getParsedAwsAccessKey(envVars), stack.getParsedAwsSecretKey(envVars),
             * stack.getAwsRegion(), false, envVars );
             */
            if (cloudFormation.create()) {
                cloudFormation.printStackOutput();
                storeOutputsInFile(envVars.get("WORKSPACE"), cloudFormation.getOutputs());
                LOGGER.info("Success");
            } else {
                LOGGER.warning("Failed");
                result = false;
            }
        }
        return result;
    }

    protected CloudFormation newCloudFormation(final PostBuildStackBean postBuildStackBean,
                                               final AbstractBuild<?, ?> build,
                                               final EnvVars env,
                                               final PrintStream logger) throws IOException {

        return new CloudFormation(logger, postBuildStackBean.getStackName(), postBuildStackBean.getOutputPrefixName(),
                                  build.getWorkspace()
                                       .child(postBuildStackBean.getCloudFormationRecipe())
                                       .readToString(), postBuildStackBean.getParsedParameters(env), postBuildStackBean.getTimeout(),
                                  postBuildStackBean.getParsedAwsAccessKey(env), postBuildStackBean.getParsedAwsSecretKey(env),
                                  postBuildStackBean.getParsedAwsRegion(env), env, false, postBuildStackBean.getSleep());

    }

    @Override
    public BuildStepDescriptor getDescriptor() {

        return DESCRIPTOR;
    }

    private File storeOutputsInFile(final String workspace, final Map<String, String> outputs) {

        final String outputFile = "aws_stack_output.properties";
        final Properties props = new Properties();
        FileWriter fw = null;
        File file = null;

        try {
            file = new File(workspace + "/" + outputFile);
            fw = new FileWriter(file);

            props.putAll(outputs);
            props.store(fw, "AWS properties");
        } catch (final Exception e) {
            e.printStackTrace();
            return null;
        }

        try {
            fw.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }

        System.out.println("Outputs stored in " + workspace + "/" + outputFile);

        return file;
    }

    @Extension
    public static final CloudFormationBuildStep.DescriptorImpl DESCRIPTOR = new CloudFormationBuildStep.DescriptorImpl();

    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public String getDisplayName() {

            return "AWS Cloud Formation";
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {

            return true;
        }

    }
}
