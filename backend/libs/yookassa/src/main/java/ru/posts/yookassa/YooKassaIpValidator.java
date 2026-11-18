package ru.posts.yookassa;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates that a remote address belongs to configured YooKassa webhook CIDR ranges (IPv4 and
 * simple IPv6 prefixes).
 */
public class YooKassaIpValidator {

    private final List<Cidr> cidrs;

    public YooKassaIpValidator(List<String> cidrNotations) {
        List<Cidr> parsed = new ArrayList<>();
        if (cidrNotations != null) {
            for (String notation : cidrNotations) {
                if (notation != null && !notation.isBlank()) {
                    parsed.add(Cidr.parse(notation.trim()));
                }
            }
        }
        this.cidrs = List.copyOf(parsed);
    }

    public boolean isAllowed(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            return isAllowed(InetAddress.getByName(ip.trim()));
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    public boolean isAllowed(InetAddress address) {
        if (address == null) {
            return false;
        }
        for (Cidr cidr : cidrs) {
            if (cidr.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private static final class Cidr {
        private final byte[] network;
        private final int prefixLength;

        private Cidr(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        static Cidr parse(String notation) {
            String networkPart;
            int prefix;
            int slash = notation.indexOf('/');
            if (slash < 0) {
                networkPart = notation;
                prefix = notation.contains(":") ? 128 : 32;
            } else {
                networkPart = notation.substring(0, slash);
                prefix = Integer.parseInt(notation.substring(slash + 1));
            }
            try {
                InetAddress networkAddress = InetAddress.getByName(networkPart);
                int max = networkAddress.getAddress().length * 8;
                if (prefix < 0 || prefix > max) {
                    throw new IllegalArgumentException("Invalid CIDR prefix: " + notation);
                }
                return new Cidr(networkAddress.getAddress(), prefix);
            } catch (UnknownHostException ex) {
                throw new IllegalArgumentException("Invalid CIDR: " + notation, ex);
            }
        }

        boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            if (prefixLength == 0) {
                return true;
            }
            BigInteger networkBits = new BigInteger(1, network);
            BigInteger candidateBits = new BigInteger(1, candidate);
            int hostBits = network.length * 8 - prefixLength;
            BigInteger mask = BigInteger.ONE
                    .shiftLeft(network.length * 8)
                    .subtract(BigInteger.ONE)
                    .shiftRight(hostBits)
                    .shiftLeft(hostBits);
            return networkBits.and(mask).equals(candidateBits.and(mask));
        }
    }
}
