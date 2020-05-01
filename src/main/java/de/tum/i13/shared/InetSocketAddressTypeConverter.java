package de.tum.i13.shared;

import picocli.CommandLine;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/// ported from : https://github.com/zegelin/cassandra-exporter/blob/master/common/src/main/java/com/zegelin/picocli/InetSocketAddressTypeConverter.java
public class InetSocketAddressTypeConverter implements CommandLine.ITypeConverter<InetSocketAddress> {
    @Override
    public InetSocketAddress convert(final String value) throws Exception {
        String[] addressParts = value.split(":");
        if (addressParts.length != 2) {
            throw new CommandLine.TypeConversionException("Need a host:port combination");
        }

        String hostname = addressParts[0].trim();
        hostname = (hostname.length() == 0 ? null : hostname); // and empty hostname == wildcard/any

        int port = 0;
        try {
            port = Integer.parseInt(addressParts[1].trim());
        } catch (final NumberFormatException e) {
            throw new CommandLine.TypeConversionException("Specified port is not a valid number");
        }

        // why can you pass a null InetAddress, but a null String hostname is an
        // error...
        try {
            return (hostname == null ? new InetSocketAddress((InetAddress) null, port)
                    : new InetSocketAddress(hostname, port));

        } catch (final IllegalArgumentException e) {
            // invalid port, etc...
            throw new CommandLine.TypeConversionException(e.getLocalizedMessage());
        }
    }
}