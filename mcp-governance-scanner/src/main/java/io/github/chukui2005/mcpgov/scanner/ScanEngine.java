package io.github.chukui2005.mcpgov.scanner;

import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.spi.ScanContext;
import io.github.chukui2005.mcpgov.core.spi.ScanRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 静态扫描执行引擎。
 * 负责调度规则集并聚合扫描发现项。
 */
public class ScanEngine {

    private static final Logger log = LoggerFactory.getLogger(ScanEngine.class);

    private final List<ScanRule> registeredRules;

    public ScanEngine(Collection<ScanRule> rules) {
        Objects.requireNonNull(rules, "规则集合不能为空");
        this.registeredRules = List.copyOf(rules);
    }

    /**
     * 获取当前引擎已注册的所有规则
     *
     * @return 只读规则列表
     */
    public List<ScanRule> getRegisteredRules() {
        return registeredRules;
    }

    /**
     * 执行扫描
     *
     * @param context 扫描上下文
     * @return 聚合后的扫描结果
     */
    public List<Finding> execute(ScanContext context) {
        Objects.requireNonNull(context, "扫描上下文不能为空");
        log.info("开始执行扫描，已注册规则数: {}", registeredRules.size());

        List<Finding> allFindings = new ArrayList<>();
        for (ScanRule rule : registeredRules) {
            try {
                List<Finding> findings = rule.scan(context);
                if (findings != null && !findings.isEmpty()) {
                    allFindings.addAll(findings);
                }
            } catch (Exception e) {
                log.error("执行规则 [{}] 异常: {}", rule.getId(), e.getMessage(), e);
            }
        }

        log.info("扫描完成，共产生发现项: {} 项", allFindings.size());
        return Collections.unmodifiableList(allFindings);
    }
}
