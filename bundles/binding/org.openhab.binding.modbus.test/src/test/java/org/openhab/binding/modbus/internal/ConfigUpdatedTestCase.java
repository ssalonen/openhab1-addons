/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.modbus.internal;

import static org.mockito.Mockito.*;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.openhab.binding.modbus.ModbusBindingProvider;
import org.openhab.core.library.types.OnOffType;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.osgi.service.cm.ConfigurationException;

import net.wimpi.modbus.procimg.SimpleDigitalIn;

/**
 * Testing how configuration update is handled
 *
 */
@RunWith(Parameterized.class)
public class ConfigUpdatedTestCase extends TestCaseSupport {

    @Parameters
    public static List<Object[]> data() {
        List<Object[]> parameters = new ArrayList<>();
        for (ServerType server : TEST_SERVERS) {
            parameters.add(new Object[] { server });
        }
        return parameters;
    }

    @SuppressWarnings("serial")
    public static class ExpectedFailure extends AssertionError {
        public ExpectedFailure(Throwable cause) {
            initCause(cause);
        }
    }

    public ConfigUpdatedTestCase(ServerType serverType) {
        super();
        this.serverType = serverType;
        // Server is a bit slower to respond than normally
        // this for the testConfigUpdatedWhilePolling
        this.artificialServerWait = 800;
    }

    @Test
    public void testConfigUpdated() throws UnknownHostException, ConfigurationException, BindingConfigParseException,
            org.osgi.service.cm.ConfigurationException {
        // Modbus server ("modbus slave") has two digital inputs
        spi.addDigitalIn(new SimpleDigitalIn(true));
        spi.addDigitalIn(new SimpleDigitalIn(false));

        binding = new ModbusBinding();

        // simulate configuration changes
        for (int i = 0; i < 2; i++) {
            binding.updated(
                    addSlave(newLongPollBindingConfig(), SLAVE_NAME, ModbusBindingProvider.TYPE_DISCRETE, null, 0, 2));
        }
        configureSwitchItemBinding(2, SLAVE_NAME, 0);
        binding.execute();

        // Give the system some time to make the expected connections & requests
        waitForRequests(1);
        if (!serverType.equals(ServerType.UDP)) {
            waitForConnectionsReceived(1);
        }

        verifyEvents();
    }

    private void verifyEvents() throws ExpectedFailure {

        verify(eventPublisher, never()).postCommand(null, null);
        verify(eventPublisher, never()).sendCommand(null, null);
        try {
            verify(eventPublisher).postUpdate("Item1", OnOffType.ON);
            verify(eventPublisher).postUpdate("Item2", OnOffType.OFF);
        } catch (AssertionError e) {
            throw new ExpectedFailure(e);
        }
        verifyNoMoreInteractions(eventPublisher);
    }

    /**
     * To verify fix for https://github.com/openhab/openhab1-addons/issues/5078
     *
     * @throws UnknownHostException
     * @throws org.osgi.service.cm.ConfigurationException
     * @throws BindingConfigParseException
     * @throws InterruptedException
     */
    @Test
    public void testConfigUpdatedWhilePolling() throws UnknownHostException, org.osgi.service.cm.ConfigurationException,
            BindingConfigParseException, InterruptedException {
        // run this test only for tcp server due to the customized connection string
        Assume.assumeTrue(serverType.equals(ServerType.TCP));
        MAX_WAIT_REQUESTS_MILLIS = 10000;

        spi.addDigitalIn(new SimpleDigitalIn(true));
        spi.addDigitalIn(new SimpleDigitalIn(false));

        binding = new ModbusBinding();

        // Customized connection settings, keep the connection open for 2000s, no connection retries, 300ms connection
        // timeout
        String connection = String.format("%s:%d:30:2000000:0:1:300", localAddress().getHostAddress(), tcpModbusPort);
        Dictionary<String, Object> cfg = addSlave(newLongPollBindingConfig(), serverType, connection, SLAVE_NAME,
                ModbusBindingProvider.TYPE_DISCRETE, null, 1, 0, 2);
        putSlaveConfigParameter(cfg, serverType, SLAVE_NAME, "updateunchangeditems", "true");
        binding.updated(cfg);
        configureSwitchItemBinding(2, SLAVE_NAME, 0);
        Thread executeOnBackground = new Thread(new Runnable() {
            @Override
            public void run() {
                binding.execute();
            }
        });

        executeOnBackground.start();
        Thread.sleep(100);
        // Connection should be now open (since ~100ms passed since connection).
        // Simulate config update
        binding.updated(cfg);

        // Polling should work after config update
        binding.execute();
        executeOnBackground.join();

        waitForRequests(2);
        waitForConnectionsReceived(2);
        verifyEvents();

    }

}
