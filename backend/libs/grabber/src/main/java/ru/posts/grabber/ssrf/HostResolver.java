package ru.posts.grabber.ssrf;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Resolves hostnames for SSRF checks (overridable in tests for DNS-rebinding scenarios). */
@FunctionalInterface
public interface HostResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;

    HostResolver SYSTEM = InetAddress::getAllByName;
}
