package org.jlamande.jenkins.plugins.aws.lambda.cloud;

import org.junit.Test;

import static org.junit.Assert.*;

public class LambdaCloudTest {

    /*@Test
    public void computeNumToLaunch() {
        try {
            LambdaCloud cloud = new LambdaCloud("cloud", "func", null, "eu-west-1");
            long computed = cloud.computeNumToLaunch(1, 0);
            assertEquals(1, computed);
            computed = cloud.computeNumToLaunch(2, 0);
            assertEquals(2, computed);
            computed = cloud.computeNumToLaunch(1, 1);
            assertEquals(0, computed);
            computed = cloud.computeNumToLaunch(2, 1);
            assertEquals(1, computed);
            computed = cloud.computeNumToLaunch(1, 2);
            assertEquals(0, computed);
            computed = cloud.computeNumToLaunch(1, 3);
            assertEquals(0, computed);
            cloud.setMaxConcurrentExecutions(5);
            computed = cloud.computeNumToLaunch(6, 4);
            assertEquals(1, computed);
        } catch(Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }*/
}
