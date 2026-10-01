package com.evacsense.config;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class MDNSRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(MDNSRegistrationService.class);
    private JmDNS jmdns;

    @PostConstruct
    public void registerService() {
        try {
            // Find the true local IP address by scanning network interfaces
            InetAddress localHost = null;
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    java.net.NetworkInterface networkInterface = interfaces.nextElement();
                // Skip loopback, inactive, or virtual adapters
                if (networkInterface.isLoopback() || !networkInterface.isUp() || networkInterface.isVirtual() || networkInterface.getName().contains("vboxnet") || networkInterface.getName().contains("wsl")) {
                    continue;
                }
                java.util.Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Get a valid Site-Local IPv4 address
                    if (!addr.isLoopbackAddress() && addr.isSiteLocalAddress() && addr.getHostAddress().indexOf(":") == -1) {
                        localHost = addr;
                        break;
                    }
                }
                if (localHost != null) break;
            }
        }
        
        if (localHost == null) {
                localHost = InetAddress.getLocalHost(); // Fallback
            }
            
            logger.info("Initializing mDNS on local host: {} (Interface scan)", localHost.getHostAddress());

            // Initialize JmDNS
            jmdns = JmDNS.create(localHost);

            // Register service: type _http._tcp.local., name "EvacSenseBackend", port 5000
            ServiceInfo serviceInfo = ServiceInfo.create("_http._tcp.local.", "EvacSenseBackend", 5000, "EvacSense API Server");
            jmdns.registerService(serviceInfo);

            logger.info("Successfully registered mDNS service: EvacSenseBackend on port 5000");

        } catch (Throwable e) {
            logger.warn("mDNS registration skipped or failed (common in cloud environments): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void unregisterService() {
        if (jmdns != null) {
            jmdns.unregisterAllServices();
            try {
                jmdns.close();
                logger.info("Unregistered mDNS services");
            } catch (IOException e) {
                logger.error("Failed to close JmDNS", e);
            }
        }
    }
}
