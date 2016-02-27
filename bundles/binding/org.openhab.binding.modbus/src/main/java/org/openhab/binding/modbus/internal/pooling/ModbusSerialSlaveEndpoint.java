package org.openhab.binding.modbus.internal.pooling;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.StandardToStringStyle;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.openhab.binding.modbus.internal.ModbusSlaveConnection;

import net.wimpi.modbus.util.SerialParameters;

/**
 * Serial endpoint. Endpoint differentiates different modbus slaves only by the serial port.
 * port.
 *
 * Endpoint contains SerialParameters which should be enough to establish the connection.
 *
 */
public class ModbusSerialSlaveEndpoint implements ModbusSlaveEndpoint {

    private SerialParameters serialParameters;
    private static StandardToStringStyle toStringStyle = new StandardToStringStyle();

    static {
        toStringStyle.setUseShortClassName(true);
    }

    public ModbusSerialSlaveEndpoint(SerialParameters serialParameters) {
        this.serialParameters = serialParameters;
    }

    public SerialParameters getSerialParameters() {
        return serialParameters;
    }

    @Override
    public <R> R accept(ModbusSlaveEndpointVisitor<R> factory) {
        return factory.visit(this);
    }

    @Override
    public ModbusSlaveConnection create(ModbusSlaveConnectionFactory factory) {
        return accept(factory);
    }

    @Override
    public int hashCode() {
        // hashcode & equal is determined purely by port name
        return serialParameters.getPortName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        // equals is determined purely by port name
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        ModbusSerialSlaveEndpoint rhs = (ModbusSerialSlaveEndpoint) obj;
        return new EqualsBuilder().append(serialParameters.getPortName(), rhs.serialParameters.getPortName())
                .isEquals();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, toStringStyle).append("portName", serialParameters.getPortName()).toString();
    }
}
