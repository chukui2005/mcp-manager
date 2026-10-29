# Task: TASK-000 - 状态机与多角色协作闭环演练 (Dry-Run)

## 状态元数据
- **Status**: DESIGN
- **Owner**: Architect
- **Approved**: No
- **Created**: 2026-10-29
- **Updated**: 2026-10-29

---

## 1. 任务背景与目标
在正式启动 Phase 1 业务代码实现之前，通过一次轻量级的空闭环演练（Dry-Run），检验并磨合多角色协作协议、门禁检查点与状态机流转：
`DESIGN → READY → IN_REVIEW → READY_TO_LAND → DONE`。

目标：
1. 验证架构师产出 ADR 与契约草稿流程；
2. 验证人类在 `.worklog/` 下书面批准（`Approved: Yes`）对状态机的解锁机制；
3. 验证工程师在隔离分支上的开发与提交规范；
4. 验证审核员提 finding 与人类最终决策的边界。

---

## 2. 验收标准 (Acceptance Criteria)
- [ ] 架构师产出演练 ADR (`docs/adr/0000-dry-run-workflow.md`)
- [ ] 人类在本文档签署 `Approved: Yes`，状态推进至 `READY`
- [ ] 工程师创建隔离分支并提交占位变更，推进至 `IN_REVIEW`
- [ ] 审核员执行只读检查，给出明确的判定 (`verdict: PASS`)
- [ ] 人类确认并完成合并，标记为 `DONE`

---

## 3. 架构设计与契约约束 (Architect)
- **相关 ADR**: `docs/adr/0000-dry-run-workflow.md`
- **相关契约**: 无（演练任务）
- **设计摘要**: 
  - 本次任务不涉及任何生产 Java 代码。
  - 仅测试文档与协作管道。

---

## 4. 人类批准记录 (Human Approval Gate)
Approved: No

---

## 5. 实现摘要 (Engineer)
- **PR 链接 / Commit Hash**: 
- **主要变更文件**:
- **测试覆盖说明**:

---

## 6. 审查与判定 (Reviewer)
- **Reviewer Verdict**: PENDING
- **Findings**:
  - [None]
