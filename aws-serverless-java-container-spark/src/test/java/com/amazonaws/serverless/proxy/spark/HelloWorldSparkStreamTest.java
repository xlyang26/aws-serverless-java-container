package com.amazonaws.serverless.proxy.spark;


import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.internal.LambdaContainerHandler;
import com.amazonaws.serverless.proxy.internal.testutils.AwsProxyRequestBuilder;
import com.amazonaws.serverless.proxy.internal.testutils.MockLambdaContext;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import spark.Spark;

import javax.servlet.http.Cookie;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static spark.Spark.get;

// This class doesn't actually test Spark. Instead it tests the proxyStream method of the
// LambdaContainerHandler object. We use the Spark implementation for this because it's the
// fastest to start
@RunWith(Parameterized.class)
public class HelloWorldSparkStreamTest {
    private static final String CUSTOM_HEADER_KEY = "X-Custom-Header";
    private static final String CUSTOM_HEADER_VALUE = "My Header Value";
    private static final String BODY_TEXT_RESPONSE = "Hello World";

    private static final String COOKIE_NAME = "MyCookie";
    private static final String COOKIE_VALUE = "CookieValue";
    private static final String COOKIE_DOMAIN = "mydomain.com";
    private static final String COOKIE_PATH = "/";

    private static SparkLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    private boolean isAlb;

    public HelloWorldSparkStreamTest(boolean alb) {
        isAlb = alb;
    }

    @Parameterized.Parameters
    public static Collection<Object> data() {
        return Arrays.asList(new Object[] { false, true });
    }

    private AwsProxyRequestBuilder getRequestBuilder() {
        AwsProxyRequestBuilder builder = new AwsProxyRequestBuilder();
        if (isAlb) builder.alb();

        return builder;
    }

    @BeforeClass
    public static void initializeServer() {
        try {
            handler = SparkLambdaContainerHandler.getAwsProxyHandler();

            configureRoutes();
            Spark.awaitInitialization();
        } catch (RuntimeException | ContainerInitializationException e) {
            e.printStackTrace();
            fail();
        }
    }

    @AfterClass
    public static void stopSpark() {
        Spark.stop();
    }

    @Test
    public void helloRequest_basicStream_populatesOutputSuccessfully() {
        InputStream req = getRequestBuilder().method("GET").path("/hello").buildStream();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            handler.proxyStream(req, outputStream, new MockLambdaContext());
            AwsProxyResponse response = LambdaContainerHandler.getObjectMapper().readValue(outputStream.toByteArray(), AwsProxyResponse.class);

            assertEquals(200, response.getStatusCode());
            assertTrue(response.getMultiValueHeaders().containsKey(CUSTOM_HEADER_KEY));
            assertEquals(CUSTOM_HEADER_VALUE, response.getMultiValueHeaders().getFirst(CUSTOM_HEADER_KEY));
            assertEquals(BODY_TEXT_RESPONSE, response.getBody());
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void nullPathRequest_doesntFail_expectA404() {
        InputStream req = getRequestBuilder().method("GET").path(null).buildStream();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            handler.proxyStream(req, outputStream, new MockLambdaContext());
            AwsProxyResponse response = LambdaContainerHandler.getObjectMapper().readValue(outputStream.toByteArray(), AwsProxyResponse.class);

            assertEquals(404, response.getStatusCode());
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

    private static void configureRoutes() {
        get("/hello", (req, res) -> {
            res.status(200);
            res.header(CUSTOM_HEADER_KEY, CUSTOM_HEADER_VALUE);
            return BODY_TEXT_RESPONSE;
        });

        get("/cookie", (req, res) -> {
            Cookie testCookie = new Cookie(COOKIE_NAME, COOKIE_VALUE);
            testCookie.setDomain(COOKIE_DOMAIN);
            testCookie.setPath(COOKIE_PATH);
            res.raw().addCookie(testCookie);
            return BODY_TEXT_RESPONSE;
        });

        get("/multi-cookie", (req, res) -> {
            Cookie testCookie = new Cookie(COOKIE_NAME, COOKIE_VALUE);
            testCookie.setDomain(COOKIE_DOMAIN);
            testCookie.setPath(COOKIE_PATH);
            Cookie testCookie2 = new Cookie(COOKIE_NAME + "2", COOKIE_VALUE + "2");
            testCookie2.setDomain(COOKIE_DOMAIN);
            testCookie2.setPath(COOKIE_PATH);
            res.raw().addCookie(testCookie);
            res.raw().addCookie(testCookie2);
            return BODY_TEXT_RESPONSE;
        });
    }
}
