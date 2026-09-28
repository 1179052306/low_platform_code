package com.security.common;


import com.common.returns.ResMsg;
import com.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.Baseboard;
import oshi.hardware.ComputerSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author lw
 * @date: 2025/1/8
 * @description:
 **/
@Service
@Slf4j
public class CrossPlatformHardwareInfo {
    public ResMsg getSystemUuidData() {
        // 测试获取硬件信息并输出结果
        ResMsg result = getHardwareInfo();
        //system.out.println(result.toString());
        return result;
    }

    /**
     * 获取主板序列号。
     *
     * @return 主板序列号字符串，如果无法获取则返回null。
     */
    private static String getBaseboardSerialNumber() {
        try {
            SystemInfo si = new SystemInfo();
            ComputerSystem computerSystem = si.getHardware().getComputerSystem();

            if (computerSystem != null) {
                Baseboard baseboard = computerSystem.getBaseboard();
                if (baseboard != null) {
                    return baseboard.getSerialNumber();
                }
            }
        } catch (Exception e) {
            log.error("通过 Oshi 获取主板序列号时发生错误: " + e.getMessage());
        }
        return null; // 如果未能获取到序列号，则返回null
    }

    /**
     * 获取计算机系统的唯一标识符（如UUID）。
     *
     * @return 系统唯一标识符字符串，如果无法获取则返回null。
     */
    private static String getSystemUuid() {
        try {
            SystemInfo si = new SystemInfo();
            ComputerSystem computerSystem = si.getHardware().getComputerSystem();
            if (computerSystem != null) {
                return computerSystem.getHardwareUUID(); // 注意这里调用的是 getSystemUUID()
            }
        } catch (Exception e) {
            log.error("通过 Oshi 获取系统 UUID 时发生错误: " + e.getMessage());
        }
        return null;
    }

    /**
     * 执行系统命令并返回输出结果。
     *
     * @param command 要执行的命令
     * @return 命令输出的结果
     * @throws IOException          如果命令执行失败或读取输出时发生错误
     * @throws InterruptedException 如果等待进程完成时被中断
     */
    private static String executeCommand(String command) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(command);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("命令执行失败，退出码：" + exitCode);
            }
            return output.toString().trim();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * 获取 Linux 主板序列号。
     *
     * @return 主板序列号字符串，如果无法获取则返回null。
     */
    private static String getLinuxBaseboardSerialNumber() {
        try {
            // 尝试使用 sudo dmidecode -s baseboard-serial-number
            return executeCommand("sudo dmidecode -s baseboard-serial-number").trim();
        } catch (IOException | InterruptedException e) {
            log.error("尝试使用 'sudo dmidecode' 获取 Linux 主板序列号时发生错误: " + e.getMessage());

            // 如果没有权限或者命令不可用，尝试使用 cat /sys/devices/virtual/dmi/id/board_serial
            try {
                return executeCommand("cat /sys/devices/virtual/dmi/id/board_serial").trim();
            } catch (IOException | InterruptedException ex) {
                log.error("尝试使用 'cat /sys/devices/virtual/dmi/id/board_serial' 获取 Linux 主板序列号时发生错误: " + ex.getMessage());
            }
        }
        return null;
    }

    /**
     * 获取 Windows 主板序列号。
     *
     * @return 主板序列号字符串，如果无法获取则返回null。
     */
    private static String getWindowsBaseboardSerialNumber() {
        // 方案一：优先尝试使用 WMIC (绝对路径)
        // 注意：Windows 11 24H2 及更新版本可能已移除此文件
        String wmicPath = "C:\\Windows\\System32\\wbem\\wmic.exe";
        String wmicCmd = wmicPath + " baseboard get serialnumber";

        try {
            // 尝试执行 WMIC
            String result = executeCommand(wmicCmd);
            if (result != null && !result.trim().isEmpty()) {
                // 如果 WMIC 执行成功且有返回，进行简单的清洗（去除表头和空行）
                return parseWmicOutput(result);
            }
        } catch (IOException e) {
            // 捕获异常
            // 如果是 "CreateProcess error=2" (找不到文件)，则进入降级方案
            if (e.getMessage() != null && e.getMessage().contains("error=2")) {
                log.warn("WMIC 未找到 (error=2)，正在切换至 PowerShell 模式...");
            } else {
                log.error("WMIC 执行发生未知错误: " + e.getMessage());
            }
        } catch (InterruptedException e) {
            log.error("WMIC 执行被中断", e);
            Thread.currentThread().interrupt();
        }

        // 方案二：降级使用 PowerShell
        // 使用 Get-CimInstance 替代 wmic，这是微软推荐的新方式
        // Select-Object -ExpandProperty 可以直接获取纯文本值，无需处理表头
        String psCmd = "powershell.exe -NoProfile -NonInteractive \"Get-CimInstance Win32_BaseBoard | Select-Object -ExpandProperty SerialNumber\"";

        try {
            String result = executeCommand(psCmd);
            if (result != null) {
                return result.trim();
            }
        } catch (Exception e) {
            log.error("PowerShell 获取主板序列号失败: " + e.getMessage());
        }

        return null;
    }
    private static String parseWmicOutput(String output) {
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            String cleanLine = line.trim();
            // 跳过空行和包含表头的行
            if (!cleanLine.isEmpty() && !cleanLine.contains("SerialNumber")) {
                return cleanLine;
            }
        }
        return null;
    }
    /**
     * 获取 macOS 设备的序列号。
     *
     * @return 设备序列号字符串，如果无法获取则返回 null。
     */
    private static String getMacBaseboardSerialNumber() {
        try {
            // 使用 system_profiler 命令获取序列号
            String serialNumber = executeCommand("system_profiler SPHardwareDataType | awk '/Serial/ {print $4}'").trim();
            if (!serialNumber.isEmpty()) {
                return serialNumber;
            }
        } catch (IOException | InterruptedException e) {
            log.error("尝试使用 'system_profiler SPHardwareDataType' 获取 macOS 序列号时发生错误: " + e.getMessage());
        }

        try {
            // 备用方案，使用 ioreg 获取序列号
            String serialNumber = executeCommand("ioreg -l | grep IOPlatformSerialNumber | awk -F '\"' '{print $4}'").trim();
            if (!serialNumber.isEmpty()) {
                return serialNumber;
            }
        } catch (IOException | InterruptedException e) {
            log.error("尝试使用 'ioreg -l' 获取 macOS 序列号时发生错误: " + e.getMessage());
        }

        return null;
    }

    /**
     * 获取硬件信息并封装到 ResMsg 对象中。
     *
     * @return 包含硬件信息的 ResMsg 对象。
     */
    public static ResMsg getHardwareInfo() {
        ResMsg resMsg = new ResMsg();
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            String serialNumber = null;
            String uuid = getSystemUuid();

            if (osName.contains("win")) {
                serialNumber = getWindowsBaseboardSerialNumber();
            } else if (osName.contains("linux")) {
                serialNumber = getLinuxBaseboardSerialNumber();
            } else if (osName.contains("mac")) {
                serialNumber = getMacBaseboardSerialNumber();
            } else {
                resMsg.setRes(false);
                resMsg.setData("不支持的操作系统: " + osName);
                return resMsg;
            }
            if (serialNumber == null && serialNumber.isEmpty() && uuid == null && uuid.isEmpty()) {
                resMsg.setRes(false);
                resMsg.setData("无法获取" + osName + "主板序列号和系统唯一标识: ");
                return resMsg;
            }
            String data = "";
            if (serialNumber != null && !serialNumber.isEmpty()) {
                serialNumber = serialNumber.trim();
                serialNumber = serialNumber.replace("SerialNumber  ", "");
                serialNumber = serialNumber.replaceAll("[\\r\\n]+", "");
                if (serialNumber.length() > 4) {
                    serialNumber = serialNumber.substring(0, 4);
                } else {
                    serialNumber = StringUtils.leftPad(String.valueOf(serialNumber), 4, "B");
                }
                data += serialNumber;
            }
            if (uuid != null && !uuid.isEmpty()) {
                uuid = uuid.trim();
                uuid = uuid.replaceAll("[\\r\\n]+", "");
                if (uuid.length() > 4) {
                    uuid = uuid.substring(0, 4);
                } else {
                    uuid = StringUtils.leftPad(String.valueOf(uuid), 4, "B");
                }
                data += uuid;
            }
            data = data.trim();
            data = data.replaceAll("[\\r\\n]+", "");
            if (data.length() < 8) {
                data = StringUtils.leftPad(String.valueOf(data), 8, "B");
            }
            resMsg.setData(data);
            // 构建 ResMsg 对象
            return resMsg;

        } catch (Exception e) {
            resMsg.setRes(false);
            resMsg.setData("发生了一个错误: " + e.getMessage());
            return resMsg;
        }
    }
}

