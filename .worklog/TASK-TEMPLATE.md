# Task: [TASK_ID] - [一句话任务描述]

## 状态元数据
- **Status**: DESIGN
- **Owner**: Architect (DESIGN) -> Engineer (READY) -> Reviewer (IN_REVIEW) -> Human (READY_TO_LAND)
- **Approved**: No
- **Created**: [YYYY-MM-DD]
- **Updated**: [YYYY-MM-DD]

---

## 1. 任务背景与目标
[清晰说明为什么要做这个任务，解决什么问题，产出物是什么]

---

## 2. 验收标准 (Acceptance Criteria)
> 必须是客观、可验证的事实，不接受主观感受。
- [ ] 标准 1
- [ ] 标准 2
- [ ] CI 全部通过 (`mvn verify`)
- [ ] 无新增架构违规

---

## 3. 架构设计与契约约束 (Architect)
- **相关 ADR**: `docs/adr/XXXX-xxxx.md`
- **相关契约**: `docs/contracts/xxxx.json`
- **设计摘要**:
  [架构师填写设计思路与文件修改边界]

---

## 4. 人类批准记录 (Human Approval Gate)
> 状态从 `DESIGN` 推进到 `READY` 的必要条件。必须为独立一行精确的 `Approved: Yes`。
Approved: No

---

## 5. 实现摘要 (Engineer)
- **PR 链接 / Commit Hash**: 
- **主要变更文件**:
- **测试覆盖说明**:

---

## 6. 审查与判定 (Reviewer)
- **Reviewer Verdict**: PENDING (PASS | ISSUES_FOUND)
- **Findings**:
  - [None]
