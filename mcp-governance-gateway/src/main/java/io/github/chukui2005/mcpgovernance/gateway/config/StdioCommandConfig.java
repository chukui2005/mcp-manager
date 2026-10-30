package io.github.chukui2005.mcpgovernance.gateway.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 本地 STDIO 命令执行配置。
 * 遵循安全基线：仅在显式配置可信命令时生效，禁止动态拼接外部输入。
 *
 * @author chukui2005
 * @since 0.1.0
 */
public class StdioCommandConfig {

    /**
     * 受信任的绝对路径或固定二进制命令。
     */
    private String command;

    /**
     * 固定的命令行入参列表。
     */
    private List<String> args = new ArrayList<>();

    /**
     * 子进程工作目录。
     */
    private String workingDirectory;

    /**
     * 默认构造函数。
     */
    public StdioCommandConfig() {
    }

    /**
     * 获取受信任的绝对路径或固定二进制命令。
     *
     * @return 命令行可执行文件路径
     */
    public String getCommand() {
        return command;
    }

    /**
     * 设置受信任的绝对路径或固定二进制命令。
     *
     * @param command 命令行可执行文件路径
     */
    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * 获取固定的命令行入参列表。
     *
     * @return 命令行入参列表
     */
    public List<String> getArgs() {
        return args;
    }

    /**
     * 设置固定的命令行入参列表。
     *
     * @param args 命令行入参列表
     */
    public void setArgs(List<String> args) {
        this.args = args != null ? args : new ArrayList<>();
    }

    /**
     * 获取子进程工作目录。
     *
     * @return 工作目录路径
     */
    public String getWorkingDirectory() {
        return workingDirectory;
    }

    /**
     * 设置子进程工作目录。
     *
     * @param workingDirectory 工作目录路径
     */
    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StdioCommandConfig that = (StdioCommandConfig) o;
        return Objects.equals(command, that.command) &&
                Objects.equals(args, that.args) &&
                Objects.equals(workingDirectory, that.workingDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, args, workingDirectory);
    }
}
