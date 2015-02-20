package com.syncapse.jenkinsci.plugins.awscloudformationwrapper;

public enum Region {
    us_east_1("us-east-1", "US East (Northern Virginia) Region", "cloudformation.us-east-1.amazonaws.com"),
    us_west_2("us-west-2", "US West (Oregon) Region", "cloudformation.us-west-2.amazonaws.com"),
    us_west_1("us-west-1", "US West (Northern California) Region", "cloudformation.us-west-1.amazonaws.com"),
    eu_west_1("eu-west-1", "EU (Ireland) Region", "cloudformation.eu-west-1.amazonaws.com"),
    eu_central_1("eu-central-1", "EU (Frankfurt) Region", "cloudformation.eu-central-1.amazonaws.com"),
    ap_southeast_1("ap-southeast-1", "Asia Pacific (Singapore) Region", "cloudformation.ap-southeast-1.amazonaws.com"),
    ap_southeast_2("ap-southeast-2", "Asia Pacific (Sydney) Region", "cloudformation.ap-southeast-2.amazonaws.com"),
    ap_northeast_1("ap-northeast-1", "Asia Pacific (Tokyo) Region", "cloudformation.ap-northeast-1.amazonaws.com"),
    sa_east_1("sa-east-1", "South America (Sao Paulo) Region", "cloudformation.sa-east-1.amazonaws.com");

    public final String shortName;
    public final String readableName;
    public final String endPoint;

    private Region(final String shortName, final String readableName, final String endPoint) {

        this.readableName = readableName;
        this.shortName = shortName;
        this.endPoint = endPoint;
    }

    public static Region getDefault() {

        return us_east_1;
    }

    public static Region getFromShortName(final String shortName) {

        return Region.valueOf(shortName.toLowerCase().replaceAll("-", "_"));
    }

}
