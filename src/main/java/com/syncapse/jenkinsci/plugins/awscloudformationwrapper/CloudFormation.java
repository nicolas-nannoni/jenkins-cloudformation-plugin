/**
 *
 */
package com.syncapse.jenkinsci.plugins.awscloudformationwrapper;

import hudson.EnvVars;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsyncClient;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.ListStacksRequest;
import com.amazonaws.services.cloudformation.model.ListStacksResult;
import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.StackSummary;
import com.google.common.collect.Lists;

/**
 * Class for interacting with CloudFormation stacks, including creating them, deleting them and getting the outputs.
 *
 * @author erickdovale
 *
 */
public class CloudFormation {

    private AmazonCloudFormationClient client;
    /**
     * Minimum time to wait before considering the creation of the stack a failure. Default value is 5 minutes. (300
     * seconds)
     */
    public static final long MIN_TIMEOUT = 300;
    private String stackName;
    private final String outputPrefixName;
    private final String recipe;
    private final List<Parameter> parameters;
    private long timeout;
    private final String awsAccessKey;
    private final String awsSecretKey;
    private final PrintStream logger;
    private final AmazonCloudFormation amazonClient;
    private Stack stack;
    private long waitBetweenAttempts;
    private final boolean autoDeleteStack;
    private final EnvVars envVars;
    private final Region awsRegion;
    private final Boolean isPrefixSelected;
    private Map<String, String> outputs;
    private long sleep = 0;

    /**
     * @param logger a logger to write progress information.
     * @param stackName the name of the stack as defined in the AWS CloudFormation API.
     * @param recipeBody the body of the json document describing the stack.
     * @param parameters a Map of where the keys are the param name and the value the param value.
     * @param timeout Time to wait for the creation of a stack to complete. This value will be the greater between
     *        {@link #MIN_TIMEOUT} and the given value.
     * @param awsAccessKey the AWS API Access Key.
     * @param awsSecretKey the AWS API Secret Key.
     */
    public CloudFormation(final PrintStream logger, final String stackName, final String outputPrefixName, final String recipeBody,
            final Map<String, String> parameters, final long timeout, final String awsAccessKey, final String awsSecretKey, final Region region,
            final boolean autoDeleteStack, final EnvVars envVars, final Boolean isPrefixSelected) {

        this.logger = logger;
        this.stackName = stackName;
        this.outputPrefixName = outputPrefixName;
        this.recipe = recipeBody;
        this.parameters = parameters(parameters);
        this.awsAccessKey = awsAccessKey;
        this.awsSecretKey = awsSecretKey;
        this.awsRegion = region != null ? region : Region.getDefault();
        this.isPrefixSelected = isPrefixSelected;

        if (timeout == -12345) {
            this.timeout = 0; // Faster testing.
            this.waitBetweenAttempts = 0;
        } else {
            this.timeout = timeout > MIN_TIMEOUT ? timeout : MIN_TIMEOUT;
            this.waitBetweenAttempts = 10; // query every 10s
        }
        this.amazonClient = getAWSClient();
        this.autoDeleteStack = autoDeleteStack;
        this.envVars = envVars;

    }

    public CloudFormation(final PrintStream logger, final String stackName, final String outputPrefixName, final String recipeBody,
            final Map<String, String> parameters, final long timeout, final String awsAccessKey, final String awsSecretKey, final Region region,
            final EnvVars envVars, final Boolean isPrefixSelected, final long sleep) {

        this.logger = logger;
        this.stackName = stackName;
        this.outputPrefixName = outputPrefixName;
        this.recipe = recipeBody;
        this.parameters = parameters(parameters);
        this.awsAccessKey = awsAccessKey;
        this.awsSecretKey = awsSecretKey;
        this.awsRegion = region != null ? region : Region.getDefault();
        this.isPrefixSelected = isPrefixSelected;
        if (timeout == -12345) {
            this.timeout = 0; // Faster testing.
            this.waitBetweenAttempts = 0;
        } else {
            this.timeout = timeout > MIN_TIMEOUT ? timeout : MIN_TIMEOUT;
            this.waitBetweenAttempts = 10; // query every 10s
        }
        this.amazonClient = getAWSClient();
        this.autoDeleteStack = false;
        this.envVars = envVars;
        this.sleep = sleep;

    }

    public CloudFormation(final PrintStream logger, final String stackName, final String outputPrefixName, final String recipeBody,
            final Map<String, String> parameters, final long timeout, final String awsAccessKey, final String awsSecretKey, final boolean autoDeleteStack,
            final EnvVars envVars, final Boolean isPrefixSelected) {

        this(logger, stackName, outputPrefixName, recipeBody, parameters, timeout, awsAccessKey, awsSecretKey, null, autoDeleteStack, envVars, isPrefixSelected);
    }

    /**
     * Return true if this stack should be automatically deleted at the end of the job, or false if it should not be
     * automatically deleted.
     *
     * @return true if this stack should be automatically deleted at the end of the job, or false if it should not be
     *         automatically deleted.
     */
    public boolean getAutoDeleteStack() {

        return autoDeleteStack;
    }

    /**
     * @return
     */
    public boolean delete() {

        if (isPrefixSelected) {
            stackName = getOldestStackNameWithPrefix();
        }
        logger.println("Deleting Cloud Formation stack: " + getExpandedStackName());
        final DeleteStackRequest deleteStackRequest = new DeleteStackRequest();
        deleteStackRequest.withStackName(getExpandedStackName());
        amazonClient.deleteStack(deleteStackRequest);
        final boolean result = waitForStackToBeDeleted();

        logger.println("Cloud Formation stack: " + getExpandedStackName() + (result ? " deleted successfully" : " failed deleting."));
        return result;
    }

    /**
     * @return True of the stack was created successfully. False otherwise.
     *
     * @throws TimeoutException if creating the stack takes longer than the timeout value passed during creation.
     *
     * @see CloudFormation#CloudFormation(PrintStream, String, String, Map, long, String, String)
     */
    public boolean create() throws TimeoutException, InterruptedException {

        logger.println("Creating Cloud Formation stack: " + getExpandedStackName());

        final CreateStackRequest request = createStackRequest();

        try {
            amazonClient.createStack(request);

            stack = waitForStackToBeCreated();

            final StackStatus status = getStackStatus(stack.getStackStatus());

            final Map<String, String> stackOutput = new HashMap<String, String>();
            if (isStackCreationSuccessful(status)) {
                final List<Output> outputs = stack.getOutputs();

                for (final Output output : outputs) {
                    stackOutput.put(output.getOutputKey(), output.getOutputValue());
                    stackOutput.put("stack_id", stack.getStackId());
                }

                logger.println("Successfully created stack: " + getExpandedStackName());
                this.outputs = stackOutput;
                Thread.sleep(TimeUnit.SECONDS.toMillis(sleep));
                return true;
            } else {
                logger.println("Failed to create stack: " + getExpandedStackName() + ". Reason: " + stack.getStackStatusReason());
                return false;
            }
        } catch (final AmazonServiceException e) {
            logger.println("Failed to create stack: " + getExpandedStackName() + ". Reason: " + detailedError(e));
            return false;
        } catch (final AmazonClientException e) {
            logger.println("Failed to create stack: " + getExpandedStackName() + ". Error was: " + e.getCause());
            return false;
        }

    }

    private String detailedError(final AmazonServiceException e) {

        final StringBuffer message = new StringBuffer();
        message.append("Detailed Message: ")
               .append(e.getMessage())
               .append('\n');
        message.append("Status Code: ")
               .append(e.getStatusCode())
               .append('\n');
        message.append("Error Code: ")
               .append(e.getErrorCode())
               .append('\n');
        return message.toString();
    }

    protected AmazonCloudFormation getAWSClient() {

        final AWSCredentials credentials = new BasicAWSCredentials(this.awsAccessKey, this.awsSecretKey);
        final AmazonCloudFormation amazonClient = new AmazonCloudFormationAsyncClient(credentials);
        amazonClient.setEndpoint(awsRegion.endPoint);
        return amazonClient;
    }

    private boolean waitForStackToBeDeleted() {

        while (true) {

            stack = getStack(amazonClient.describeStacks());

            if (stack == null) {
                return true;
            }

            final StackStatus stackStatus = getStackStatus(stack.getStackStatus());

            if (StackStatus.DELETE_COMPLETE == stackStatus) {
                return true;
            }

            if (StackStatus.DELETE_FAILED == stackStatus) {
                return false;
            }

            sleep();

        }

    }

    private List<Parameter> parameters(final Map<String, String> parameters) {

        if (parameters == null || parameters.values()
                                            .size() == 0) {
            return null;
        }

        final List<Parameter> result = Lists.newArrayList();
        Parameter parameter = null;
        for (final String name : parameters.keySet()) {
            parameter = new Parameter();
            parameter.setParameterKey(name);
            parameter.setParameterValue(parameters.get(name));
            result.add(parameter);
        }

        return result;
    }

    private Stack waitForStackToBeCreated() throws TimeoutException {

        final DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest().withStackName(getExpandedStackName());
        StackStatus status = StackStatus.CREATE_IN_PROGRESS;
        Stack stack = null;
        final long startTime = System.currentTimeMillis();
        while (isStackCreationInProgress(status)) {
            if (isTimeout(startTime)) {
                throw new TimeoutException("Timed out waiting for stack to be created. (timeout=" + timeout + ")");
            }
            stack = getStack(amazonClient.describeStacks(describeStacksRequest));
            status = getStackStatus(stack.getStackStatus());
            if (isStackCreationInProgress(status)) {
                sleep();
            }
        }

        printStackEvents();

        return stack;
    }

    private void printStackEvents() {

        final DescribeStackEventsRequest r = new DescribeStackEventsRequest();
        r.withStackName(getExpandedStackName());
        final DescribeStackEventsResult describeStackEvents = amazonClient.describeStackEvents(r);

        final List<StackEvent> stackEvents = describeStackEvents.getStackEvents();
        Collections.reverse(stackEvents);

        for (final StackEvent event : stackEvents) {
            logger.println(event.getEventId() + " - " + event.getResourceType() + " - " + event.getResourceStatus() + " - " + event.getResourceStatusReason());
        }

    }

    public void printStackOutput() {

        if (outputs != null) {
            logger.println("**** " + getExpandedStackName() + " outputs: ****");
            for (final Entry<String, String> output : outputs.entrySet()) {
                logger.println(output.getKey() + ": " + output.getValue());
            }
        }

    }

    private boolean isTimeout(final long startTime) {

        return timeout == 0 ? false : (System.currentTimeMillis() - startTime) > (timeout * 1000);
    }

    private Stack getStack(final DescribeStacksResult result) {

        for (final Stack aStack : result.getStacks()) {
            if (getExpandedStackName().equals(aStack.getStackName())) {
                return aStack;
            }
        }

        return null;

    }

    private boolean isStackCreationSuccessful(final StackStatus status) {

        return status == StackStatus.CREATE_COMPLETE;
    }

    private void sleep() {

        try {
            Thread.sleep(waitBetweenAttempts * 1000);
        } catch (final InterruptedException e) {
            if (stack != null) {
                logger.println("Received an interruption signal. There is a stack created or in the proces of creation. Check in your amazon account to ensure you are not charged for this.");
                logger.println("Stack details: " + stack);
            }
        }
    }

    private boolean isStackCreationInProgress(final StackStatus status) {

        return status == StackStatus.CREATE_IN_PROGRESS;
    }

    private StackStatus getStackStatus(final String status) {

        final StackStatus result = StackStatus.fromValue(status);
        return result;
    }

    private CreateStackRequest createStackRequest() {

        final CreateStackRequest r = new CreateStackRequest();
        r.withStackName(getExpandedStackName());
        r.withParameters(parameters);
        r.withTemplateBody(recipe);
        r.withCapabilities("CAPABILITY_IAM");

        return r;
    }

    public Map<String, String> getOutputs() {

        // Prefix outputs with stack name to prevent collisions with other stacks created in the same build.
        final HashMap<String, String> map = new HashMap<String, String>();
        for (final String key : outputs.keySet()) {
            map.put(getExpandedStackName() + "_" + key, outputs.get(key));

            // Additionally, set the same set of outputs with the given prefix name (if any)
            if (outputPrefixName != null && !outputPrefixName.isEmpty()) {
                map.put(outputPrefixName + "_" + key, outputs.get(key));
            }
        }

        return map;
    }

    private String getExpandedStackName() {

        return envVars.expand(stackName);
    }

    private String getOldestStackNameWithPrefix() {

        final List<StackSummary> stackSummaries = getAllRunningStacks();
        final ArrayList<StackSummary> filteredStackSummries = new ArrayList<StackSummary>();
        final List<String> stackNames = new ArrayList<String>();
        for (final StackSummary summary : stackSummaries) {
            if (summary.getStackName()
                       .startsWith(stackName)) {
                filteredStackSummries.add(summary);
            }
        }
        if (filteredStackSummries.isEmpty() || filteredStackSummries.size() == 1) {
            return stackName;
        }
        return returnOldestStackName(filteredStackSummries);

    }

    private String returnOldestStackName(final ArrayList<StackSummary> filteredStackSummries) {

        Date date = filteredStackSummries.get(0)
                                         .getCreationTime();
        String stackToDelete = "";
        for (final StackSummary summary : filteredStackSummries) {
            if (summary.getCreationTime()
                       .before(date))
                ;
            {
                date = summary.getCreationTime();
                stackToDelete = summary.getStackName();
            }
        }

        return stackToDelete;
    }

    private List<StackSummary> getAllRunningStacks() {

        client = new AmazonCloudFormationClient(new AWSCredentials() {

            public String getAWSAccessKeyId() {

                return awsAccessKey;
            }

            public String getAWSSecretKey() {

                return awsSecretKey;
            }
        });
        final List<String> stackStatusFilters = new ArrayList<String>();
        stackStatusFilters.add("UPDATE_COMPLETE");
        stackStatusFilters.add("CREATE_COMPLETE");
        stackStatusFilters.add("ROLLBACK_COMPLETE");
        final ListStacksRequest listStacksRequest = new ListStacksRequest();
        listStacksRequest.setStackStatusFilters(stackStatusFilters);
        final ListStacksResult result = client.listStacks(listStacksRequest);
        final List<StackSummary> stackSummaries = result.getStackSummaries();
        return stackSummaries;
    }

    public Map<String, String> getStackParameters(final String stackName) {

        final DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
        describeStacksRequest.setStackName(stackName);
        final DescribeStacksResult describeStacksResult = client.describeStacks(describeStacksRequest);
        final List<Stack> stacks = describeStacksResult.getStacks();
        final Stack stack = stacks.get(0);
        final List<Parameter> parameters = stack.getParameters();
        final Map<String, String> map = new LinkedHashMap<String, String>();
        for (final Parameter parameter : parameters) {
            map.put(parameter.getParameterKey(), parameter.getParameterValue());
        }
        return map;
    }
}
