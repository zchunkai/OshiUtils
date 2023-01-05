package cn.platform.thinglinks.pcWorkHost.utils;

import cn.platform.thinglinks.pcWorkHost.domain.SystemInfo;
import cn.platform.thinglinks.pcWorkHost.domain.SystemProcessInfo;
import cn.platform.thinglinks.pcWorkHost.domain.WorkHostMsg;
import lombok.extern.slf4j.Slf4j;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import oshi.util.Util;

import java.io.*;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * @description OshiUtil工具类
 * @className OshiUtils
 * @author zck
 *@date 2022/12/27 14:14
 **/
@Slf4j
public class OshiUtils {
    private static oshi.SystemInfo systemInfo=new oshi.SystemInfo();

    public static void main(String[] args) throws IOException {
//        SystemInfo systemMsg = getSystemMsg(new WorkHostMsg());
//        System.out.println(systemMsg);
//        getLoad(new WorkHostMsg());
    }

    /**
     *获取系统信息
     * */
    public static SystemInfo getSystemMsg(WorkHostMsg hostMsg){
        try {
            HardwareAbstractionLayer hal = systemInfo.getHardware();
            OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
            Properties props = System.getProperties();
            GlobalMemory memory = hal.getMemory();
            SystemInfo info = new SystemInfo();
            /***系统内存***/
            memory(memory,info);
            /**系统CPU使用率***/
            cpu(hal.getProcessor(),info);
            /**操作系统****/
            os(props,info);
            /**系统安装时间****/
            info.setSystemInstallTime(getInstallDate(info));
            /**操作系统位数****/
            info.setSystemType(String.valueOf(operatingSystem.getBitness()));
            /**主机名称***/
            info.setHostName(operatingSystem.getNetworkParams().getHostName());
            /**ip4地址****/
            info.setIpAddress(InetAddress.getLocalHost().getHostAddress());
            /**获取硬盘信息****/
            file(operatingSystem,info);
            /**版本信息***/
            info.setSystemVersion(operatingSystem.getVersionInfo().getBuildNumber());
            return info;
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取系统内存
     * ***/
    public static SystemInfo memory(GlobalMemory memory,SystemInfo info){
        /**剩余内存 kb***/
        info.setFreeMemory(memory.getAvailable() / 1024L);
        /**最大可使用内存 kb***/
        info.setMaxMemory(memory.getTotal() / 1024L);
        /**使用内存 kb***/
        info.setTotalMemory(info.getMaxMemory()-info.getFreeMemory());
        /**系统内存使用率***/
        info.setSystemMemoryUse(FormatUtil.formatDouble((double) info.getTotalMemory()/ (double) info.getMaxMemory() * 100, 1));
        return info;
    }
    /**
     * 获取cpu使用率
     */
    public static SystemInfo cpu(CentralProcessor processor,SystemInfo info){
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        Util.sleep(1000);
        long[] ticks = processor.getSystemCpuLoadTicks();
        long nice = ticks[CentralProcessor.TickType.NICE.getIndex()] - prevTicks[CentralProcessor.TickType.NICE.getIndex()];
        long irq = ticks[CentralProcessor.TickType.IRQ.getIndex()] - prevTicks[CentralProcessor.TickType.IRQ.getIndex()];
        long softirq = ticks[CentralProcessor.TickType.SOFTIRQ.getIndex()] - prevTicks[CentralProcessor.TickType.SOFTIRQ.getIndex()];
        long steal = ticks[CentralProcessor.TickType.STEAL.getIndex()] - prevTicks[CentralProcessor.TickType.STEAL.getIndex()];
        long cSys = ticks[CentralProcessor.TickType.SYSTEM.getIndex()] - prevTicks[CentralProcessor.TickType.SYSTEM.getIndex()];
        long user = ticks[CentralProcessor.TickType.USER.getIndex()] - prevTicks[CentralProcessor.TickType.USER.getIndex()];
        long iowait = ticks[CentralProcessor.TickType.IOWAIT.getIndex()] - prevTicks[CentralProcessor.TickType.IOWAIT.getIndex()];
        long idle = ticks[CentralProcessor.TickType.IDLE.getIndex()] - prevTicks[CentralProcessor.TickType.IDLE.getIndex()];
        long totalCpu = user + nice + cSys + idle + iowait + irq + softirq + steal;
        info.setSystemCpuUse(Double.parseDouble(new DecimalFormat("#.##").format(1.0 - (idle * 1.0 / totalCpu))));
        info.setCpuName(processor.getProcessorIdentifier().getName());
        return info;
    }


    /**
     * 获取硬盘信息
     * ***/
    public static SystemInfo file(OperatingSystem operatingSystem,SystemInfo info){
        long totalSpace = 0;
        long freeSpace = 0;
        long usedSpace = 0;
        FileSystem fileSystem = operatingSystem.getFileSystem();
        List<OSFileStore> fileStores = fileSystem.getFileStores();
        for (OSFileStore fileStore : fileStores) {
            /**总大小****/
            totalSpace =totalSpace+fileStore.getTotalSpace()/1024;
            /**空闲****/
            freeSpace =freeSpace+fileStore.getFreeSpace()/1024;

        }
        /**使用***/
        usedSpace = totalSpace - freeSpace;

        info.setTotalSystemFile(totalSpace);
        info.setFreeSystemFile(freeSpace);
        info.setUsedSystemFile(usedSpace);
        return info;
    }


    /***
     * 获取操作系统
     * */
    public static SystemInfo os(Properties props,SystemInfo info){
        /**操作系统****/
        info.setSystemName(props.get("os.name").toString());
        /**登录用户****/
        info.setUserName(props.get("user.name").toString());
        return info;
    }


    /**
     * 获取安装日期
     * ***/
    public static String getInstallDate(SystemInfo info){
        try {
            String line;
            String command = null;
            String charsetName=null;
            String replace=null;
            String os=null;
            switch (info.getSystemName()){
                case "Linux":
                    command="rpm -qi basesystem";
                    charsetName="UTF8";
                    replace="Install Date";
                    os="linux";
                    break;
                case "Windows 10":
                    command="systeminfo";
                    charsetName="GBK";
                    replace="初始安装日期";
                    os="windows";
                    break;
                default:
                    break;
            }
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader input =
                    new BufferedReader
                            (new InputStreamReader(p.getInputStream(),charsetName));
            while ((line = input.readLine()) != null) {
                if (line.contains(replace))break;
            }
            input.close();
            if (line!=null){
                line=line.replace(replace+":","").trim();
            }
            line=line.replace(","," ");
            if ("linux".equals(os)){
                String[] s = line.split(" ");
                line=s[0]+" "+s[2];
                line = DateUtil.formatDate(DateUtil.parseDate(line, "yyyy年MM月dd日 HH时mm分ss秒"), "yyyy/MM/dd HH:mm:ss");
            }
            return line;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 获取进程信息
     * ***/
    public static SystemProcessInfo getLoad(WorkHostMsg hostMsg){
        log.info("开始时间:{}",new Date());
        OperatingSystem os = systemInfo.getOperatingSystem();
        GlobalMemory memory = systemInfo.getHardware().getMemory();
        try {
            List<OSProcess> procs = os.getProcesses();
            OSProcess osProcess = procs.get(1);
            for (int i = 0; i < procs.size() && i < 5; i++) {
                OSProcess p = procs.get(i);
                double v = FormatUtil.formatDouble(100d * (p.getKernelTime() + p.getUserTime()) / p.getUpTime(), 2);
                double v1 = FormatUtil.formatDouble(100d * p.getResidentSetSize() / memory.getTotal(), 2);
//                return appState;

            }

        } catch (Exception e) {
            log.error("获取进程信息错误", e);
        }
        return null;
    }

}
