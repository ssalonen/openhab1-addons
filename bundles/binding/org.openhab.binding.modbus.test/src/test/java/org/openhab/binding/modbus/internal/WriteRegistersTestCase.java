package org.openhab.binding.modbus.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.wimpi.modbus.msg.ModbusRequest;
import net.wimpi.modbus.msg.ReadCoilsRequest;
import net.wimpi.modbus.msg.ReadInputDiscretesRequest;
import net.wimpi.modbus.msg.ReadInputRegistersRequest;
import net.wimpi.modbus.msg.ReadMultipleRegistersRequest;
import net.wimpi.modbus.msg.WriteCoilRequest;
import net.wimpi.modbus.msg.WriteMultipleRegistersRequest;
import net.wimpi.modbus.msg.WriteSingleRegisterRequest;
import net.wimpi.modbus.procimg.DigitalIn;
import net.wimpi.modbus.procimg.DigitalOut;
import net.wimpi.modbus.procimg.Register;
import net.wimpi.modbus.procimg.SimpleDigitalIn;
import net.wimpi.modbus.procimg.SimpleDigitalOut;
import net.wimpi.modbus.procimg.SimpleInputRegister;
import net.wimpi.modbus.procimg.SimpleRegister;

import org.apache.commons.lang.NotImplementedException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.openhab.binding.modbus.ModbusBindingProvider;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.model.item.binding.BindingConfigParseException;


/**
 * 
 * FIXME: trialing whitespace
 * FIXME: errors might be due to ???
 * 			if (! newState.equals(currentState)) {
				eventPublisher.postUpdate(itemName, newState);
			}
		 in modbus binding. I.e. the s
 * FIXME: switch item written to floats
 * 
 * @author salsam
 *
 */

@RunWith(Parameterized.class)
public class WriteRegistersTestCase extends TestCaseSupport {

	/**
	 * When we are expecting write multiple registers request (instead of write single register)
	 */
	private static boolean expectingWriteMultiple(String valueType){
		return valueType.endsWith("32");
	}

	private static final int READ_COUNT = 4;
	private static Command[] BOOL_COMMANDS = new Command[] { OnOffType.OFF,
		OpenClosedType.CLOSED ,OnOffType.ON,
			OpenClosedType.OPEN };

	@SuppressWarnings("serial")
	public static class ExpectedFailure extends AssertionError {
	}

	@Parameters
	public static List<Object[]> parameters() {
		List<Object[]> parameters = WriteRegistersTestParameters.parameters();
		// Uncomment the following to run subset of tests 
//		return parameters.subList(0, 5);
		return parameters;
	}

	private boolean nonZeroOffset;
	private String valueType;
	private String type;
	private int itemIndex;
	private Command command;
	private short[] expectedValue;
	private boolean expectingAssertionError;
	private short[] registerInitialValues;
	private State itemInitialState;


	/*
	 * @param serverType
	 * @param registerInitialValues
	 *            initial registers (each short representing register from index 0)
	 * @itemInitialState item initial state
	 * @param nonZeroOffset
	 *            whether to test non-zero start address in modbus binding
	 * @param type
	 *            type of the slave (e.g. "holding")
	 * @param valueType value type to use for items
	 * @param itemIndex
	 *            index of the item that receives command
	 * @param command
	 *            received command
	 * @param expectedValue
	 *            expected registers written to registers (in register order).
	 * @param expectingAssertionError
	 */	
	public WriteRegistersTestCase(ServerType serverType, short[] registerInitialValues, State itemInitialState, boolean nonZeroOffset, String type, String valueType,
			int itemIndex, Command command, short[] expectedValue, boolean expectingAssertionError) {
		this.serverType = serverType;
		this.registerInitialValues = registerInitialValues;
		this.itemInitialState = itemInitialState;
		this.nonZeroOffset = nonZeroOffset;
		this.type = type;
		this.valueType = valueType;
		this.itemIndex = itemIndex;
		this.command = command;
		this.expectedValue = expectedValue;
		this.expectingAssertionError = expectingAssertionError;
	}

	@Before
	public void setUp() throws Exception {
		super.setUp();
		initSpi();
	}

	/**
	 * Test writing
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRegistersNoReads() throws Exception {
		binding = new ModbusBinding();
		int offset = (nonZeroOffset ? 1 : 0);
		binding.updated(addSlave(newLongPollBindingConfig(), SLAVE_NAME,
				type, null, offset, 2));
		configureItems();

		try {
			binding.receiveCommand(String.format("Item%s", itemIndex + 1),
					command);
		} catch (NullPointerException e) {
			if(type != ModbusBindingProvider.TYPE_HOLDING){
				fail("Expecting NullPointerException only with holding");				
			}
			return;
		}
		if(type == ModbusBindingProvider.TYPE_HOLDING){
			String msg = "Should have raised NullPointerException with holding";
			errPrint(msg);
			fail(msg);
		}
		errPrint("verifying: testRegistersNoReads");
		verifyRequests(false);
	}
	
	private void configureItems() throws BindingConfigParseException {
		if(Arrays.asList(BOOL_COMMANDS).contains(command) || ModbusBindingProvider.VALUE_TYPE_BIT.equals(type)){
			configureSwitchItemBinding(2, SLAVE_NAME, 0, "", itemInitialState);
		}else{
			configureNumberItemBinding(2, SLAVE_NAME, 0, "", itemInitialState);
		}
		
	}
	
	@Test
	public void testWriteRegistersAfterRead() throws Exception {
		binding = new ModbusBinding();
		int offset = (nonZeroOffset ? 1 : 0);
		binding.updated(addSlave(newLongPollBindingConfig(), SLAVE_NAME,
				type, null, offset, READ_COUNT));
		configureItems();

		// READ -- initializes register
		binding.execute();

		binding.receiveCommand(String.format("Item%s", itemIndex + 1), command);
		errPrint("verifying: testWriteDigitalsAfterRead");
		verifyRequests(true);
	}

	private void verifyRequests(boolean readRequestExpected) throws Exception {
		try {
			ArrayList<ModbusRequest> requests = modbustRequestCaptor
					.getAllReturnValues();
			int expectedRegisterWriteStartIndex = nonZeroOffset ? (itemIndex + 1) : itemIndex;
			boolean writeExpected = type == ModbusBindingProvider.TYPE_HOLDING;
			int expectedRequests = (writeExpected ? 1 : 0)
					+ (readRequestExpected ? 1 : 0);			
			// We always get single connection in, since the connection is established immediately
			int expectedConnections = 1;
			
			// Give the system 5 seconds to make the expected connections & requests
			waitForConnectionsReceived(expectedConnections);
			waitForRequests(expectedRequests);
			
			assertThat(requests.size(), is(equalTo(expectedRequests)));

			if (readRequestExpected) {
				if (type == ModbusBindingProvider.TYPE_INPUT) {
					assertThat(requests.get(0),
							is(instanceOf(ReadInputRegistersRequest.class)));
				} else if (type == ModbusBindingProvider.TYPE_HOLDING) {
					assertThat(requests.get(0),
							is(instanceOf(ReadMultipleRegistersRequest.class)));
				} else {
					throw new RuntimeException();
				}
			}
			if (writeExpected) {
				Register[] registers;
				if (expectingWriteMultiple(valueType)) {
					assertThat(requests.get(expectedRequests - 1),
							is(instanceOf(WriteMultipleRegistersRequest.class)));
					WriteMultipleRegistersRequest writeRequest = (WriteMultipleRegistersRequest) requests
							.get(expectedRequests - 1);
					assertThat(writeRequest.getReference(),
							is(equalTo(expectedRegisterWriteStartIndex)));
					if(expectedValue == null && expectingAssertionError){
						String msg = "Did not receive any AssertionErrors even though expected!";
						errPrint(msg);
						throw new RuntimeException(msg);
					}
					registers = writeRequest.getRegisters();
				} else {
					assertThat(requests.get(expectedRequests - 1),
							is(instanceOf(WriteSingleRegisterRequest.class)));
					WriteSingleRegisterRequest writeRequest = (WriteSingleRegisterRequest) requests
							.get(expectedRequests - 1);
					assertThat(writeRequest.getReference(),
							is(equalTo(expectedRegisterWriteStartIndex)));
					if(expectedValue == null && expectingAssertionError){
						String msg = "Did not receive any AssertionErrors even though expected!";
						errPrint(msg);
						throw new RuntimeException(msg);
					}
					registers = new Register[]{ writeRequest.getRegister() };			
				}
				short[] registersAsShort = new short[registers.length];
				for(int i = 0; i < registers.length; i++){
					registersAsShort[i] = registers[i].toShort();
				}
				assertThat(registersAsShort, is(equalTo(expectedValue)));
			}
		} catch (AssertionError e) {
			if (expectingAssertionError) {
				errPrint("Expected failure");
				e.printStackTrace(System.err);
				return;
			} else {
				errPrint("Unexpected assertion error " + e.getMessage());
				e.printStackTrace(System.err);
				throw e;
			}
		}
		if (expectingAssertionError) {
			errPrint("Did not get assertion error (as expected)");
			throw new AssertionError(
					"Did not get assertion error (as expected)");
		} else {
			errPrint("OK");
		}
	}
	
	private void errPrint(String description){
		System.err.println(String.format("%s (%s): registerInitialValues=%s, nonZeroOffset=%s, type=%s, valueType=%s, itemIndex=%d, command=%s, expectedValue=%s, expectingAssertionError=%s", 
				description, serverType, Arrays.toString(registerInitialValues), nonZeroOffset, type,  valueType,
				itemIndex, command, Arrays.toString(expectedValue), expectingAssertionError));
	}

	private void initSpi() {
		int registerCount = registerInitialValues.length;
		for (int i = 0; i < registerCount; i++) {
			if (ModbusBindingProvider.TYPE_HOLDING.equals(type)) {
				spi.addRegister(new SimpleRegister(registerInitialValues[i]));
			} else if (ModbusBindingProvider.TYPE_INPUT.equals(type)) {
				spi.addInputRegister(new SimpleInputRegister(
						registerInitialValues[i]));
			}
		}
	}
}
