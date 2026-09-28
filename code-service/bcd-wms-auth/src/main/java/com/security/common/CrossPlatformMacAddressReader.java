package com.security.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.logging.Level;

/**
 * @author lw
 * @date: 2025/1/3
 * @description:
 **/
@Service
@Slf4j
public class CrossPlatformMacAddressReader {

    public  String getMacAddress() {
        try {
            // Try using Java standard library first
            return getMacAddressFromNetworkInterface();
        } catch (SocketException | UnknownHostException e) {
            log.error("执行异常：" + "Failed to get MAC address from NetworkInterface(无法从NetworkInterface获取MAC地址)" + e);

        }

        // If the above method fails, try executing system commands
        return getMacAddressFromCommand();
    }

    private  String getMacAddressFromNetworkInterface() throws SocketException, UnknownHostException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface network = networkInterfaces.nextElement();
            if (!network.isLoopback() && network.isUp()) {
                byte[] mac = network.getHardwareAddress();
                if (mac != null) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        sb.append(String.format("%02X%s", b, "-"));
                    }
                    if (sb.length() > 0) {
                        sb.setLength(sb.length() - 1); // Remove last separator
                    }
                    return sb.toString();
                }
            }
        }
        throw new SocketException("No suitable network interface found");
    }

    private  String getMacAddressFromCommand() {
        String osName = System.getProperty("os.name").toLowerCase();
        String command = null;
        String pattern = null;

        if (osName.contains("win")) {
            command = "getmac";
            pattern = "-"; // Windows getmac output format is XX-XX-XX-XX-XX-XX
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            command = "ifconfig";
            pattern = ":"; // Linux ifconfig output format is XX:XX:XX:XX:XX:XX
        }

        if (command == null) {
            log.warn("执行异常：" + "Unsupported operating system detected(检测到不支持的操作系统)");
            return null;
        }

        try {
            Process process = Runtime.getRuntime().exec(command);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (pattern != null && line.contains(pattern)) {
                        if (osName.contains("win")) {
                            // For Windows, the MAC address is in the first column of the output
                            String[] parts = line.split("\\s+");
                            if (parts.length > 0 && parts[0].matches("[0-9A-F]{2}(-[0-9A-F]{2}){5}")) {
                                return parts[0];
                            }
                        } else {
                            // For Unix-like systems, look for 'ether' keyword before the MAC address
                            if (line.contains("ether")) {
                                String[] parts = line.split(" ");
                                for (String part : parts) {
                                    if (part.matches("[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}")) {
                                        return part;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {

            log.error(Level.SEVERE + "Failed to execute command or read output(执行命令或读取输出失败)" + e);
        }

        return null;
    }
}
