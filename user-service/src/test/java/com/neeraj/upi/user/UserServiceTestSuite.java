package com.neeraj.upi.user;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * JUnit 5 Test Suite for the User Service module.
 * You can run this class directly from your IDE to execute all tests within this microservice.
 * 
 * Note: If this fails to run, ensure you have the 'junit-platform-suite' dependency 
 * in your pom.xml.
 */
@Suite
@SelectPackages("com.neeraj.upi.user")
public class UserServiceTestSuite {
    // This class remains empty.
    // The @Suite and @SelectPackages annotations tell JUnit to find and run 
    // all test classes inside the "com.neeraj.upi.user" package and its subpackages.
}
