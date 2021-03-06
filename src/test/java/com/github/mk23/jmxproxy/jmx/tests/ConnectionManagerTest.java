package com.github.mk23.jmxproxy.jmx.tests;

import com.github.mk23.jmxproxy.conf.AppConfig;
import com.github.mk23.jmxproxy.jmx.ConnectionCredentials;
import com.github.mk23.jmxproxy.jmx.ConnectionManager;

import com.github.mk23.jmxproxy.tests.AuthenticatedTests;

import io.dropwizard.util.Duration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import java.lang.management.ManagementFactory;

import java.util.Arrays;
import java.util.UUID;

import javax.management.ObjectName;

import javax.ws.rs.WebApplicationException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.junit.experimental.categories.Category;

import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConnectionManagerTest {
    private final String passwdFile       = System.getProperty("com.sun.management.jmxremote.password.file");

    private final String validHost        = "localhost:" + System.getProperty("com.sun.management.jmxremote.port");
    private final String invalidPort      = "localhost:0";
    private final String invalidHost      = "192.0.2.1:0";

    private final String localMBean       = "ConnectionManagerTest:type=test";
    private final String validMBean       = "java.lang:type=OperatingSystem";
    private final String invalidMBean     = "java.lang:type=InvalidMBean";

    private final String validAttribute   = "Name";
    private final String invalidAttribute = "InvalidAttribute";

    private final ConnectionCredentials validAuth;
    private final ConnectionCredentials invalidAuth = new ConnectionCredentials(UUID.randomUUID().toString(), UUID.randomUUID().toString());

    @Rule public ExpectedException thrown = ExpectedException.none();
    @Rule public TestName name = new TestName();

    public interface ConnectionManagerTestJMXMBean {
        public void setReadOnlyAttribute(int value);
    }

    public class ConnectionManagerTestJMX implements ConnectionManagerTestJMXMBean {
        public void setReadOnlyAttribute(int value) {
            // ignore
        }
    }

    public ConnectionManagerTest() throws Exception {
        if (passwdFile != null) {
            String[] creds = new BufferedReader(new FileReader(new File(passwdFile))).readLine().split("\\s+");
            validAuth = new ConnectionCredentials(creds[0], creds[1]);
        } else {
            validAuth = null;
        }

        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(
                new ConnectionManagerTestJMX(), new ObjectName(localMBean)
            );
        } catch (javax.management.InstanceAlreadyExistsException e) {
        }
    }

    @Before
    public void printTestName() {
        System.out.println(" -> " + name.getMethodName());
    }

    /* Auth tests */
    @Test
    @Category(AuthenticatedTests.class)
    public void checkValidHostValidAuth() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        assertNotNull(manager.getHost(validHost, validAuth));
    }
    @Test
    @Category(AuthenticatedTests.class)
    public void checkValidHostNoAuth() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("");
        manager.getHost(validHost);
    }
    @Test
    @Category(AuthenticatedTests.class)
    public void checkValidHostNullAuth() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 401 Unauthorized");
        manager.getHost(validHost, null);
    }
    @Test
    @Category(AuthenticatedTests.class)
    public void checkValidHostInvalidAuth() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 401 Unauthorized");
        manager.getHost(validHost, invalidAuth);
    }
    @Test
    @Category(AuthenticatedTests.class)
    public void checkValidHostValidAuthSwitch() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        assertNotNull(manager.getHost(validHost, validAuth));
        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 401 Unauthorized");
        manager.getHost(validHost, invalidAuth);
    }
    @Test
    @Category(AuthenticatedTests.class)
    public void checkValidHostAnonymousAuthDisallowed() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 401 Unauthorized");
        manager.getHost(validHost);
    }
    @Test
    public void checkValidHostAnonymousAuthAllowed() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        assertNotNull(manager.getHost(validHost));
    }

    /* Host tests */
    @Test
    public void checkValidHost() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        assertNotNull(manager.getHost(validHost, validAuth));
    }

    @Test(timeout=2000)
    public void checkUnknownHost() throws Exception {
        AppConfig serviceConfig = new AppConfig();
        serviceConfig.setConnectTimeout(Duration.milliseconds(500));

        final ConnectionManager manager = new ConnectionManager(serviceConfig);

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 404 Not Found");

        manager.getHost(invalidHost, validAuth);
    }

    @Test
    public void checkBrokenHost() throws Exception {
        AppConfig serviceConfig = new AppConfig();
        serviceConfig.setConnectTimeout(Duration.milliseconds(500));

        final ConnectionManager manager = new ConnectionManager(serviceConfig);

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 400 Bad Request");

        manager.getHost("\n", validAuth);
    }

    @Test
    public void checkPartialHost() throws Exception {
        AppConfig serviceConfig = new AppConfig();
        serviceConfig.setConnectTimeout(Duration.milliseconds(500));

        final ConnectionManager manager = new ConnectionManager(serviceConfig);

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 404 Not Found");

        manager.getHost(validHost.split(":")[0], validAuth);
    }

    @Test
    public void checkInvalidPort() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 404 Not Found");

        assertNull(manager.getHost(invalidPort, validAuth));
    }

    @Test
    public void checkValidFullHostWhitelist() throws Exception {
        AppConfig serviceConfig = new AppConfig();
        serviceConfig.setAllowedEndpoints(Arrays.asList(new String[] { validHost }));

        final ConnectionManager manager = new ConnectionManager(serviceConfig);

        assertNotNull(manager.getHost(validHost, validAuth));
    }

    @Test
    public void checkValidBareHostWhitelist() throws Exception {
        AppConfig serviceConfig = new AppConfig();
        serviceConfig.setAllowedEndpoints(Arrays.asList(new String[] { validHost.split(":")[0] }));

        final ConnectionManager manager = new ConnectionManager(serviceConfig);

        assertNotNull(manager.getHost(validHost, validAuth));
    }

    @Test
    public void checkInvalidPortValidFullHostWhitelist() throws Exception {
        AppConfig serviceConfig = new AppConfig();
        serviceConfig.setAllowedEndpoints(Arrays.asList(new String[] { validHost }));

        final ConnectionManager manager = new ConnectionManager(serviceConfig);

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 403 Forbidden");

        manager.getHost(invalidPort, validAuth);
    }

    @Test
    public void checkInvalidPortValidBareHostWhitelist() throws Exception {
        AppConfig serviceConfig = new AppConfig();
        serviceConfig.setAllowedEndpoints(Arrays.asList(new String[] { validHost.split(":")[0] }));

        final ConnectionManager manager = new ConnectionManager(serviceConfig);

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 404 Not Found");

        manager.getHost(invalidPort, validAuth);
    }

    /* MBean tests */
    @Test
    public void checkValidHostMBeans() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        assertTrue(manager.getHost(validHost, validAuth).getMBeans().contains(validMBean));
    }

    @Test
    public void checkValidHostValidMBean() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        assertNotNull(manager.getHost(validHost, validAuth).getMBean(validMBean));
    }

    @Test
    public void checkValidHostInvalidMBean() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        assertNull(manager.getHost(validHost, validAuth).getMBean(invalidMBean));
    }

    /* Attribute tests */
    @Test
    public void checkValidHostValidMBeanAttributes() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        assertTrue(manager.getHost(validHost, validAuth).getMBean(validMBean).getAttributes().contains(validAttribute));
    }

    @Test
    public void checkValidHostValidMBeanValidAttribute() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        assertNotNull(manager.getHost(validHost, validAuth).getMBean(validMBean).getAttribute(validAttribute));
    }

    @Test
    public void checkValidHostValidMBeanInvalidAttribute() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        assertNull(manager.getHost(validHost, validAuth).getMBean(validMBean).getAttribute(invalidAttribute));
    }

    @Test
    public void checkValidHostValidMBeanInvalidAttributes() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        assertTrue(manager.getHost(validHost, validAuth).getMBean(validMBean).getAttributes(invalidAttribute, 1).isEmpty());
    }

    @Test
    public void checkValidHostValidMBeanReadOnlyAttribute() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig());

        assertTrue(manager.getHost(validHost, validAuth).getMBean(validMBean).getAttribute("ReadOnlyAttribute") == null);
    }

    /* Custom MBean tests */
    @Test
    public void checkValidHostRemovedMBean() throws Exception {
        final ConnectionManager manager = new ConnectionManager(new AppConfig().setCacheDuration(Duration.seconds(3)));

        assertNotNull(manager.getHost(validHost, validAuth).getMBean(localMBean));

        ManagementFactory.getPlatformMBeanServer().unregisterMBean(
            new ObjectName(localMBean)
        );

        java.lang.Thread.sleep(Duration.seconds(5).toMilliseconds());

        assertNull(manager.getHost(validHost, validAuth).getMBean(localMBean));
    }
}
