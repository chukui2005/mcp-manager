package io.github.chukui2005.mcpgov.scanner.sarif;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.chukui2005.mcpgov.core.model.Finding;
import io.github.chukui2005.mcpgov.core.model.PhysicalLocation;
import io.github.chukui2005.mcpgov.core.spi.ScanRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 符合 OASIS SARIF 2.1.0 标准与 docs/contracts/scan-result.schema.json 契约的报告序列化器。
 */
public class SarifSerializer {

    private static final String SARIF_SCHEMA = "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json";
    private static final String SARIF_VERSION = "2.1.0";
    private static final String DRIVER_NAME = "mcp-governance-scanner";
    private static final String DRIVER_VERSION = "0.1.0";
    private static final String DRIVER_URI = "https://github.com/chukui2005/mcp-manager";

    private final ObjectMapper objectMapper;

    public SarifSerializer() {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 将规则集与扫描结果转换为 SARIF 2.1.0 JSON 字符串
     *
     * @param rules 参与扫描的规则集合
     * @param findings 扫描命中的结果集合
     * @return 格式化后的 SARIF JSON
     */
    public String serialize(Collection<ScanRule> rules, Collection<Finding> findings) {
        Objects.requireNonNull(rules, "rules 集合不能为空");
        Objects.requireNonNull(findings, "findings 集合不能为空");

        Map<String, Object> sarif = new LinkedHashMap<>();
        sarif.put("$schema", SARIF_SCHEMA);
        sarif.put("version", SARIF_VERSION);

        List<Map<String, Object>> runs = new ArrayList<>();
        Map<String, Object> run = new LinkedHashMap<>();

        // 构建 tool.driver
        Map<String, Object> tool = new LinkedHashMap<>();
        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("name", DRIVER_NAME);
        driver.put("version", DRIVER_VERSION);
        driver.put("informationUri", DRIVER_URI);

        List<Map<String, Object>> ruleList = new ArrayList<>();
        for (ScanRule rule : rules) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", rule.getId());
            r.put("name", rule.getName());
            r.put("shortDescription", Map.of("text", rule.getShortDescription()));
            r.put("fullDescription", Map.of("text", rule.getName() + " - " + rule.getRemediation()));
            r.put("help", Map.of(
                    "text", rule.getRemediation(),
                    "markdown", "**修复建议**: " + rule.getRemediation()
            ));
            r.put("defaultConfiguration", Map.of("level", rule.getDefaultSeverity().getSarifLevel()));
            r.put("properties", Map.of(
                    "owaspCategory", rule.getCategory().name(),
                    "remediation", rule.getRemediation()
            ));
            ruleList.add(r);
        }
        driver.put("rules", ruleList);
        tool.put("driver", driver);
        run.put("tool", tool);

        // 构建 results
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Finding finding : findings) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("ruleId", finding.ruleId());
            res.put("level", finding.severity().getSarifLevel());
            res.put("message", Map.of("text", finding.message()));

            List<Map<String, Object>> locations = new ArrayList<>();
            for (PhysicalLocation loc : finding.locations()) {
                Map<String, Object> physicalLocation = new LinkedHashMap<>();
                Map<String, Object> artifactLocation = new LinkedHashMap<>();
                artifactLocation.put("uri", loc.uri());
                physicalLocation.put("artifactLocation", artifactLocation);

                if (loc.startLine() != null) {
                    Map<String, Object> region = new LinkedHashMap<>();
                    region.put("startLine", loc.startLine());
                    if (loc.startColumn() != null) region.put("startColumn", loc.startColumn());
                    if (loc.endLine() != null) region.put("endLine", loc.endLine());
                    if (loc.endColumn() != null) region.put("endColumn", loc.endColumn());
                    physicalLocation.put("region", region);
                }

                locations.add(Map.of("physicalLocation", physicalLocation));
            }
            res.put("locations", locations.isEmpty() ? List.of(Map.of("physicalLocation", Map.of("artifactLocation", Map.of("uri", "unknown")))) : locations);

            Map<String, Object> properties = new LinkedHashMap<>();
            if (finding.targetServer() != null) properties.put("targetServer", finding.targetServer());
            if (finding.targetTool() != null) properties.put("targetTool", finding.targetTool());
            if (finding.sha256() != null) properties.put("sha256", finding.sha256());
            if (!finding.riskTags().isEmpty()) properties.put("riskTags", new ArrayList<>(finding.riskTags()));
            if (!properties.isEmpty()) res.put("properties", properties);

            resultList.add(res);
        }
        run.put("results", resultList);
        runs.add(run);
        sarif.put("runs", runs);

        try {
            return objectMapper.writeValueAsString(sarif);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 SARIF 2.1.0 报告失败", e);
        }
    }
}
