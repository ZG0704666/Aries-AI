/* METADATA
{
  "name": "workflow",
  "description": "工作流管理工具，提供工作流的创建、查询、更新、删除和触发执行功能。",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "get_all_workflows",
      "description": "获取所有工作流列表，包括工作流的基本信息（ID、名称、描述、状态等）。",
      "parameters": []
    },
    {
      "name": "create_workflow",
      "description": "创建一个新的工作流。可以同时创建节点和连接。Execute节点的actionType是工具名称，actionConfig是工具参数。",
      "parameters": [
        { "name": "name", "description": "工作流名称", "type": "string", "required": true },
        { "name": "description", "description": "工作流描述（可选）", "type": "string", "required": false },
        { "name": "nodes", "description": "节点数组的JSON字符串。Execute节点格式：{type:'execute',name:'节点名',actionType:'工具名',actionConfig:{参数键值对}}。例如：[{type:'trigger',name:'手动触发',triggerType:'manual'},{type:'execute',name:'读文件',actionType:'read_file',actionConfig:{path:'/sdcard/test.txt'}}]", "type": "string", "required": false },
        { "name": "connections", "description": "连接数组的JSON字符串（可选）。格式：[{sourceNodeId:'节点1ID',targetNodeId:'节点2ID'}]", "type": "string", "required": false },
        { "name": "enabled", "description": "是否启用（可选，默认true）", "type": "boolean", "required": false }
      ]
    },
    {
      "name": "get_workflow",
      "description": "获取指定工作流的完整详情，包括所有节点和连接。",
      "parameters": [
        { "name": "workflow_id", "description": "工作流 ID", "type": "string", "required": true }
      ]
    },
    {
      "name": "update_workflow",
      "description": "更新指定工作流的信息、节点或连接。Execute节点的actionType是工具名称，actionConfig是工具参数。",
      "parameters": [
        { "name": "workflow_id", "description": "工作流 ID", "type": "string", "required": true },
        { "name": "name", "description": "新的工作流名称（可选）", "type": "string", "required": false },
        { "name": "description", "description": "新的工作流描述（可选）", "type": "string", "required": false },
        { "name": "nodes", "description": "新的节点数组的JSON字符串（可选）。Execute节点格式：{type:'execute',name:'节点名',actionType:'工具名',actionConfig:{参数键值对}}", "type": "string", "required": false },
        { "name": "connections", "description": "新的连接数组的JSON字符串（可选）。格式：[{sourceNodeId:'节点1ID',targetNodeId:'节点2ID'}]", "type": "string", "required": false },
        { "name": "enabled", "description": "是否启用（可选）", "type": "boolean", "required": false }
      ]
    },
    {
      "name": "delete_workflow",
      "description": "删除指定的工作流。",
      "parameters": [
        { "name": "workflow_id", "description": "工作流 ID", "type": "string", "required": true }
      ]
    },
    {
      "name": "trigger_workflow",
      "description": "触发指定工作流的执行。需要提供工作流的 ID。",
      "parameters": [
        { "name": "workflow_id", "description": "工作流 ID", "type": "string", "required": true }
      ]
    }
  ]
}
*/
const WorkflowIntegration = (function () {
    /**
     * 获取所有工作流
     * @returns 工作流列表
     */
    async function get_all_workflows() {
        const data = await toolCall("get_all_workflows", {});
        return {
            success: true,
            message: "成功获取工作流列表",
            data
        };
    }
    /**
     * 创建新工作流
     * @param params 工作流参数
     * @returns 创建结果
     */
    async function create_workflow(params) {
        const callParams = {
            name: params.name,
            description: params.description || ""
        };
        // 处理 nodes 参数
        if (params.nodes) {
            if (typeof params.nodes === 'string') {
                callParams.nodes = params.nodes;
            }
            else {
                callParams.nodes = JSON.stringify(params.nodes);
            }
        }
        // 处理 connections 参数
        if (params.connections) {
            if (typeof params.connections === 'string') {
                callParams.connections = params.connections;
            }
            else {
                callParams.connections = JSON.stringify(params.connections);
            }
        }
        // 处理 enabled 参数
        if (params.enabled !== undefined) {
            callParams.enabled = params.enabled;
        }
        const data = await toolCall("create_workflow", callParams);
        return {
            success: true,
            message: "成功创建工作流",
            data
        };
    }
    /**
     * 获取工作流详情
     * @param params 获取参数
     * @returns 工作流详情
     */
    async function get_workflow(params) {
        const data = await toolCall("get_workflow", params);
        return {
            success: true,
            message: "成功获取工作流详情",
            data
        };
    }
    /**
     * 更新工作流
     * @param params 更新参数
     * @returns 更新结果
     */
    async function update_workflow(params) {
        const callParams = {
            workflow_id: params.workflow_id
        };
        // 只包含提供的参数
        if (params.name !== undefined)
            callParams.name = params.name;
        if (params.description !== undefined)
            callParams.description = params.description;
        if (params.enabled !== undefined)
            callParams.enabled = params.enabled;
        // 处理 nodes 参数
        if (params.nodes) {
            if (typeof params.nodes === 'string') {
                callParams.nodes = params.nodes;
            }
            else {
                callParams.nodes = JSON.stringify(params.nodes);
            }
        }
        // 处理 connections 参数
        if (params.connections) {
            if (typeof params.connections === 'string') {
                callParams.connections = params.connections;
            }
            else {
                callParams.connections = JSON.stringify(params.connections);
            }
        }
        const data = await toolCall("update_workflow", callParams);
        return {
            success: true,
            message: "成功更新工作流",
            data
        };
    }
    /**
     * 删除工作流
     * @param params 删除参数
     * @returns 删除结果
     */
    async function delete_workflow(params) {
        const data = await toolCall("delete_workflow", params);
        return {
            success: true,
            message: "成功删除工作流",
            data
        };
    }
    /**
     * 触发工作流执行
     * @param params 触发参数
     * @returns 执行结果
     */
    async function trigger_workflow(params) {
        const data = await toolCall("trigger_workflow", params);
        return {
            success: true,
            message: "成功触发工作流",
            data
        };
    }
    /**
     * 包装工具执行，处理错误和结果
     * @param func 要执行的函数
     * @param params 函数参数
     */
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({ success: false, message: String(error && error.message ? error.message : error) });
        }
    }
    return {
        get_all_workflows: () => wrapToolExecution(get_all_workflows, {}),
        create_workflow: (params) => wrapToolExecution(create_workflow, params),
        get_workflow: (params) => wrapToolExecution(get_workflow, params),
        update_workflow: (params) => wrapToolExecution(update_workflow, params),
        delete_workflow: (params) => wrapToolExecution(delete_workflow, params),
        trigger_workflow: (params) => wrapToolExecution(trigger_workflow, params)
    };
})();
exports.get_all_workflows = WorkflowIntegration.get_all_workflows;
exports.create_workflow = WorkflowIntegration.create_workflow;
exports.get_workflow = WorkflowIntegration.get_workflow;
exports.update_workflow = WorkflowIntegration.update_workflow;
exports.delete_workflow = WorkflowIntegration.delete_workflow;
exports.trigger_workflow = WorkflowIntegration.trigger_workflow;
