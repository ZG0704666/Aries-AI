/* METADATA
{
    name: "Automatic_ui_subagent"
    description: '''
兼容AutoGLM，提供基于独立UI控制器模型（例如 autoglm-phone-9b）的高层UI自动化子代理工具，用于根据自然语言意图自动规划并执行点击/输入/滑动等一系列界面操作。
当用户提出需要帮忙完成某个界面操作任务（例如打开应用、搜索内容、在多个页面之间完成一套步骤）时，可以调用本包由子代理自动规划和执行具体步骤。
'''

    tools: [
        {
            name: "usage_advice"
            description: '''
 UI子代理使用建议：

 - 会话复用（重要）：多次调用尽量复用同一个 agent_id（沿用上一次返回的 data.agentId），保持在同一虚拟屏/同一应用上下文内。
 - 对话无状态（重要）：每次调用对子代理都是全新对话，intent 需要自带上下文，建议固定模板：
   当前任务已经完成: ...
   你需要在此基础上进一步完成: ...
   可能用到的信息: ...
 - 意图必须自包含（重要）：禁止使用“这五个/继续/同上/刚才说的”等指代。
   - 多对象任务必须在“可能用到的信息”里给出清单（按界面顺序），并明确当前要处理哪个（例如第1个酒店）。
   - 若清单未知，本次调用先让子代理从当前页面识别并复述清单，再进行下一步（必要时拆成多次调用）。
 - 对齐推进（重要）：不要一次调用里同时做“收集清单 + 处理清单全部对象”。应拆成：先清单，再按 A→B→C 逐项处理。
 - 并行优先（重要）：互不依赖的子任务（多平台搜索/多入口确认/同一对象的信息提取分工）优先用 run_subagent_parallel 并行；并行时每个子代理建议不同 agent_id 避免互相干扰。
 - 并行资源约束（重要）：并行分支数必须受“可用独立App数量/可用虚拟屏数量”限制。
   - 同一个App/同一个包名，不能同时存在于两个虚拟屏/两个 agent_id 中并行操作（会导致应用状态错乱/坏掉）。
   - 并行分支数不得超过“可同时存在的独立App数量”（例如只有2个独立App可用，就最多2并行）。
   - 并行调用必须传入 target_app_i=目标应用名（每个 intent_i 对应一个 target_app_i），用于冲突检测；所有启用分支的 target_app_i 必须互不相同。
   - 第2次/第N次并行（重试或第二轮任务）不得因为“想更快”而擅自提高并行度；应保持同样的并行上限，只重试失败分支，或改为串行。
 - 失败与完成（重要）：半成功/误判完成不算完成；应继续纠错推进。仅在连续 2-3 次失败仍无法推进时才停止，并明确失败原因与可选替代方案。
 - 例子（详细：1并行 + 2串行）

   例子1（并行：多平台同时找同一酒店的差评要点）
   目标：同时在 大众点评/美团/携程 搜索“XX酒店”，各自提取“近一年差评Top3要点 + 原文引用 + 日期”，最后主Agent合并。
   调用流程：
   1) 主Agent 一次并行：
      - 调用 run_subagent_parallel，给每个子代理一个互不干扰的 agent_id（例如 dp_1 / mt_1 / xc_1）。
      - intent_1（大众点评）示例：
        当前任务已经完成: 无
        你需要在此基础上进一步完成: 打开大众点评，搜索“XX酒店”，进入酒店详情，进入评价/差评/低分区，提取近一年差评Top3要点，并把每条要点附带1句原文引用+日期。
        可能用到的信息: 目标酒店名=XX酒店；输出格式=1)要点 2)引用 3)日期；如果搜到多个同名酒店，必须先确认地址/商圈与目标一致。
      - intent_2（美团）/ intent_3（携程）同理，各自写清 app、路径、输出格式。
   2) 主Agent 汇总：读取并行返回 results，合并三个平台的提取结果。
   3) 只重试失败分支：若 results 里只有美团分支失败，则只对美团再发起一次（不要重跑点评/携程）。
      - 例如再次调用 run_subagent（或再次 run_subagent_parallel 但只填 intent_2），并补充纠错信息：
        当前任务已经完成: 上次在美团搜索到酒店列表，但未能进入评价页（可能入口在“点评/评价”Tab）。
        你需要在此基础上进一步完成: 重新打开美团搜索“XX酒店”，进入正确的酒店详情页，找到“评价/点评”入口并进入差评/低分区，按同样格式输出Top3。
        可能用到的信息: 若页面出现“住客点评/全部评价/差评”多入口，优先选择“全部评价”再筛选“差评/低分”。

   例子2（串行：先清单，再按 A→B→C 逐项处理）
   目标：在携程酒店列表页，先列出前5家酒店；然后只处理第1家酒店的差评要点；处理完再处理第2家……
   调用流程：
   1) 清单阶段（一次 run_subagent）：
      - 主Agent 调用 run_subagent(intent=..., agent_id 留空)
      - intent 示例：
        当前任务已经完成: 无
        你需要在此基础上进一步完成: 打开携程，搜索“杭州 西湖 酒店”，进入列表页；把当前屏幕能看到的酒店按从上到下顺序列出前5个（名称+价格/评分如可见），并停留在列表页不要进入详情。
        可能用到的信息: 输出必须包含清单序号1-5；若需要滚动才能凑满5个可以滚动一次，但仍要保持顺序。
      - 该次返回 data.agentId 记为 A（后续复用）
   2) 单对象阶段：处理“清单第1家”（一次 run_subagent，复用 agent_id=A）
      - intent 示例：
        当前任务已经完成: 已获得酒店清单（1)酒店A 2)酒店B 3)酒店C 4)酒店D 5)酒店E），当前停留在列表页。
        你需要在此基础上进一步完成: 进入酒店A详情页，找到评价页并筛选差评/低分，提取差评Top3要点（每条含1句原文引用）。完成后返回列表页并确认列表顶部仍是酒店A/B/C顺序。
        可能用到的信息: 目标对象=清单第1家=酒店A；如果进入后发现标题不是酒店A则立刻返回列表并重新点击正确条目。
   3) 继续处理第2家/第3家……（每次都复用 agent_id=A，并在 intent 中显式写“已完成/下一步/关键信息”，以及“当前目标=清单第2家=酒店B”）。

   例子3（串行：发消息，强调“页面确认+会话复用”）
   目标：在微信给“张三”发送“我到楼下了”，并确认发送成功。
   调用流程：
   1) 打开并定位会话（一次 run_subagent）：
      - 主Agent 调用 run_subagent(intent=..., agent_id 留空)
      - intent 示例：
        当前任务已经完成: 无
        你需要在此基础上进一步完成: 打开微信，进入聊天列表，搜索联系人“张三”，打开与“张三”的聊天页面；必须确认页面顶部标题=张三。
        可能用到的信息: 如果搜索结果有多个“张三”，需要根据头像/备注/地区等二次确认；不确定时返回并说明。
      - 该次返回 data.agentId 记为 W（后续复用）
   2) 发送并二次确认（一次 run_subagent，复用 agent_id=W）：
      - intent 示例：
        当前任务已经完成: 已打开与“张三”的聊天页，顶部标题=张三。
        你需要在此基础上进一步完成: 输入并发送消息“我到楼下了”；发送后在消息列表中确认该消息出现在最新一条且无明显发送失败标记（如红色感叹号）。
        可能用到的信息: 若出现权限弹窗/键盘遮挡，先处理弹窗再继续；若发送失败，尝试重发一次并说明原因。
 '''
            parameters: []
        }

        {
            name: "run_subagent"
            description: '''
 运行内置UI子代理（使用独立UI控制器模型）根据高层意图自动规划并执行一系列UI操作，例如自动点击、滑动、输入等。

 推荐：用于“单个明确子目标”或“清单阶段/单对象阶段”。多平台并行搜索、多入口确认等独立子任务优先用 run_subagent_parallel。
 '''
            parameters: [
                {
                    name: "intent"
                    description: "任务意图描述，例如：'打开微信并发送一条消息' 或 '在B站搜索某个视频'"
                    type: "string"
                    required: true
                }
                {
                    name: "max_steps"
                    description: "最大执行步数，默认20，可根据任务复杂度调整。"
                    type: "number"
                    required: false
                }
                {
                    name: "agent_id"
                    description: "可选：用于复用虚拟屏幕会话的 agentId。建议在多次调用时尽量传入同一个 agent_id（可沿用上一次返回的 data.agentId），否则会新建会话导致上下文/虚拟屏切换。"
                    type: "string"
                    required: false
                }
            ]
        }

        {
            name: "run_subagent_parallel"
            description: '''
并行运行 1-4 个 UI 子代理。

 注意：并行调用时，每个子代理对它自身都是全新对话，因此 intent_1..4 需要由主Agent分别写清楚“已完成/下一步/关键信息”。
 建议并行时每个子代理使用不同的 agent_id（或留空让系统自动创建），避免操作同一虚拟屏幕造成冲突。
 如果并行任务中仅有部分子代理失败，则只对失败的子代理继续发起后续调用（补充纠错信息、提高约束），不要让已成功的子代理重复执行。
 每个 intent_i 必须自包含；建议不同 agent_id；只重试失败的 intent_i。
 典型场景：多平台并行搜索；同一对象多入口确认/交叉校验；把“同一对象A”的分工并行做完后再进入B。
 '''
            parameters: [
                {
                    name: "intent_1"
                    description: "第1个子代理意图（推荐使用：当前任务已经完成/你需要进一步完成/可能用到的信息 三段式）"
                    type: "string"
                    required: true
                }
                {
                    name: "target_app_1"
                    description: "第1个子代理目标应用名（必填，用于并行冲突检测；各分支必须不同）"
                    type: "string"
                    required: true
                }
                {
                    name: "max_steps_1"
                    description: "第1个子代理最大步数（默认20）"
                    type: "number"
                    required: false
                }
                {
                    name: "agent_id_1"
                    description: "第1个子代理 agent_id（可选，用于会话复用；并行建议不同）"
                    type: "string"
                    required: false
                }

                {
                    name: "intent_2"
                    description: "第2个子代理意图（可选）"
                    type: "string"
                    required: false
                }
                {
                    name: "target_app_2"
                    description: "第2个子代理目标应用名（当 intent_2 存在时必填；各分支必须不同）"
                    type: "string"
                    required: false
                }
                {
                    name: "max_steps_2"
                    description: "第2个子代理最大步数（默认20）"
                    type: "number"
                    required: false
                }
                {
                    name: "agent_id_2"
                    description: "第2个子代理 agent_id（可选）"
                    type: "string"
                    required: false
                }

                {
                    name: "intent_3"
                    description: "第3个子代理意图（可选）"
                    type: "string"
                    required: false
                }
                {
                    name: "target_app_3"
                    description: "第3个子代理目标应用名（当 intent_3 存在时必填；各分支必须不同）"
                    type: "string"
                    required: false
                }
                {
                    name: "max_steps_3"
                    description: "第3个子代理最大步数（默认20）"
                    type: "number"
                    required: false
                }
                {
                    name: "agent_id_3"
                    description: "第3个子代理 agent_id（可选）"
                    type: "string"
                    required: false
                }

                {
                    name: "intent_4"
                    description: "第4个子代理意图（可选）"
                    type: "string"
                    required: false
                }
                {
                    name: "target_app_4"
                    description: "第4个子代理目标应用名（当 intent_4 存在时必填；各分支必须不同）"
                    type: "string"
                    required: false
                }
                {
                    name: "max_steps_4"
                    description: "第4个子代理最大步数（默认20）"
                    type: "number"
                    required: false
                }
                {
                    name: "agent_id_4"
                    description: "第4个子代理 agent_id（可选）"
                    type: "string"
                    required: false
                }
            ]
        }
    ]
 }
*/
const UIAutomationSubAgentTools = (function () {
    let cachedAgentId;
    async function usage_advice(_params) {
        return {
            success: true,
            message: 'UI子代理使用建议',
            data: {
                advice: "会话复用：尽量复用 agent_id（沿用 data.agentId）.\n" +
                    "对话无状态：每次调用是新对话，intent 写清 已完成/下一步/关键信息.\n" +
                    "自包含：别用‘这五个/继续/同上’；多对象要么列清单+当前目标，要么先让子代理在当前页识别并复述清单.\n" +
                    "对齐+并行：先清单后逐项(A→B→C)；独立子任务/同一对象多入口优先并行(run_subagent_parallel)，只重试失败分支.\n" +
                    "并行资源约束：并行分支数必须受可用独立App/虚拟屏数量限制；同一个App/包名不能同时出现在两个虚拟屏/两个agent_id 中并行操作（会坏）。并行调用必须传 target_app_i=目标应用名，且各分支 target_app_i 不能重复；第2次/第N次并行不得擅自提高并行度，保持上限，只重试失败分支或改串行。\n" +
                    "失败与完成：半成功不算完成；未达成目标继续推进，连续 2-3 次失败再停并说明原因.\n" +
                    "\n" +
                    "例子1（并行，多平台差评）：run_subagent_parallel 分别写清点评/美团/携程的三段式 intent，并为每个分支使用不同 agent_id；只重试失败分支.\n" +
                    "例子2（串行，先清单后逐项）：第1次 run_subagent 只产出清单并返回 agentId=A；后续每次 run_subagent 复用 A，且在 intent 里显式写‘已完成=清单… 当前目标=第k个… 下一步=只处理该对象’，按 A→B→C 推进.\n" +
                    "例子3（串行，发消息）：第1次打开并确认聊天页标题；第2次复用同一 agent_id 发送并确认发送成功/失败标记.",
            },
        };
    }
    async function run_subagent(params) {
        const { intent, max_steps, agent_id } = params;
        const agentIdToUse = (agent_id && String(agent_id).length > 0) ? String(agent_id) : cachedAgentId;
        const result = await Tools.UI.runSubAgent(intent, max_steps, agentIdToUse);
        if (result && result.agentId) {
            cachedAgentId = String(result.agentId);
        }
        return {
            success: true,
            message: 'UI子代理执行完成',
            data: result,
        };
    }
    async function run_subagent_parallel(params) {
        const slots = [1, 2, 3, 4];
        const activeSlots = slots
            .map((i) => {
                const intent = params[`intent_${i}`];
                if (!intent || String(intent).trim().length === 0)
                    return null;
                const targetApp = params[`target_app_${i}`];
                return { index: i, targetApp };
            })
            .filter(Boolean);
        const missingTargets = activeSlots
            .filter((s) => s.targetApp === undefined || s.targetApp === null || String(s.targetApp).trim().length === 0)
            .map((s) => s.index);
        if (missingTargets.length > 0) {
            return {
                success: false,
                message: `并行参数错误：intent_${missingTargets.join(', intent_')} 缺少 target_app_${missingTargets.join(', target_app_')}（目标应用名）。并行时必须为每个启用分支传入目标应用名，用于检测“同一应用不能出现在两个虚拟屏/agent_id”的冲突。`,
            };
        }
        const used = {};
        for (const s of activeSlots) {
            const key = String(s.targetApp).trim().toLowerCase();
            const prev = used[key];
            if (prev !== undefined) {
                return {
                    success: false,
                    message: `并行参数错误：target_app_${prev} 与 target_app_${s.index} 重复（同一目标应用=“${String(s.targetApp).trim()}”）。同一应用不能同时在两个虚拟屏/agent_id 中并行操作。`,
                };
            }
            used[key] = s.index;
        }
        const tasks = slots
            .map((i) => {
                const intent = params[`intent_${i}`];
                if (!intent || String(intent).trim().length === 0)
                    return null;
                const maxSteps = params[`max_steps_${i}`];
                const agentId = params[`agent_id_${i}`];
                return (async () => {
                    try {
                        const result = await Tools.UI.runSubAgent(String(intent), maxSteps === undefined ? undefined : Number(maxSteps), agentId === undefined || agentId === null || String(agentId).length === 0 ? undefined : String(agentId));
                        return { index: i, success: true, result };
                    }
                    catch (e) {
                        return { index: i, success: false, error: (e === null || e === void 0 ? void 0 : e.message) || String(e) };
                    }
                })();
            })
            .filter(Boolean);
        const results = await Promise.all(tasks);
        const okCount = results.filter((r) => r.success).length;
        return {
            success: true,
            message: `并行UI子代理执行完成：成功 ${okCount} 个 / 共 ${results.length} 个`,
            data: {
                results,
            },
        };
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }
    return {
        usage_advice: (params) => wrapToolExecution(usage_advice, params),
        run_subagent: (params) => wrapToolExecution(run_subagent, params),
        run_subagent_parallel: (params) => wrapToolExecution(run_subagent_parallel, params),
    };
})();
exports.usage_advice = UIAutomationSubAgentTools.usage_advice;
exports.run_subagent = UIAutomationSubAgentTools.run_subagent;
exports.run_subagent_parallel = UIAutomationSubAgentTools.run_subagent_parallel;
