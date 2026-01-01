/* METADATA
{
  "name": "tasker",
  "description": "集成 Tasker 插件事件触发工具，通过本包可向 Tasker 发送事件。",
  "enabledByDefault": false,
  "tools": [
    {
      "name": "trigger_tasker_event",
      "description": "触发一个 Tasker 事件。使用 task_type 指定事件类型，可传 arg1..arg5 或 args_json。",
      "parameters": [
        { "name": "task_type", "description": "事件类型标识", "type": "string", "required": true },
        { "name": "arg1", "description": "可选参数1", "type": "string", "required": false },
        { "name": "arg2", "description": "可选参数2", "type": "string", "required": false },
        { "name": "arg3", "description": "可选参数3", "type": "string", "required": false },
        { "name": "arg4", "description": "可选参数4", "type": "string", "required": false },
        { "name": "arg5", "description": "可选参数5", "type": "string", "required": false },
        { "name": "args_json", "description": "以JSON形式传递任意参数", "type": "string", "required": false }
      ]
    }
  ]
}
*/
/// <reference path="./types/index.d.ts" />
const TaskerIntegration = (function () {
    async function trigger_tasker_event(params) {
        const data = await Tools.Tasker.triggerEvent(params);
        return {
            success: true,
            message: "Tasker 事件已触发",
            data
        };
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params || {});
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({ success: false, message: String(error && error.message ? error.message : error) });
        }
    }
    return {
        trigger_tasker_event: (params) => wrapToolExecution(trigger_tasker_event, params)
    };
})();
exports.trigger_tasker_event = TaskerIntegration.trigger_tasker_event;
