/**
 * Workflow type definitions for Assistance Package Tools
 * 
 * This file provides type definitions for workflow-related functionality.
 */

import {
    WorkflowListResultData,
    WorkflowResultData,
    WorkflowDetailResultData,
    WorkflowNode,
    WorkflowNodeConnection,
    TriggerNode,
    ExecuteNode,
    NodePosition,
    StringResultData
} from './results';

/**
 * Workflow namespace containing workflow-related types and utilities
 */
export namespace Workflow {
    /**
     * Node position in the workflow canvas
     */
    export type Position = NodePosition;

    /**
     * Trigger node configuration
     */
    export interface Trigger extends TriggerNode { }

    /**
     * Execute node configuration
     */
    export interface Execute extends ExecuteNode { }

    /**
     * Workflow node (union type)
     */
    export type Node = WorkflowNode;

    /**
     * Workflow connection between nodes
     */
    export interface Connection extends WorkflowNodeConnection { }

    /**
     * Basic workflow information
     */
    export interface Info extends WorkflowResultData { }

    /**
     * Detailed workflow information with nodes and connections
     */
    export interface Detail extends WorkflowDetailResultData { }

    /**
     * Workflow list response
     */
    export interface List extends WorkflowListResultData { }

    /**
     * Parameters for creating a workflow
     */
    export interface CreateParams {
        /** Workflow name */
        name: string;
        /** Workflow description (optional) */
        description?: string;
        /** Nodes array or JSON string (optional) */
        nodes?: Node[] | string;
        /** Connections array or JSON string (optional) */
        connections?: Connection[] | string;
        /** Whether the workflow is enabled (optional, default true) */
        enabled?: boolean;
    }

    /**
     * Parameters for getting a workflow
     */
    export interface GetParams {
        /** Workflow ID */
        workflow_id: string;
    }

    /**
     * Parameters for updating a workflow
     */
    export interface UpdateParams {
        /** Workflow ID */
        workflow_id: string;
        /** New workflow name (optional) */
        name?: string;
        /** New workflow description (optional) */
        description?: string;
        /** New nodes array or JSON string (optional) */
        nodes?: Node[] | string;
        /** New connections array or JSON string (optional) */
        connections?: Connection[] | string;
        /** Whether the workflow is enabled (optional) */
        enabled?: boolean;
    }

    /**
     * Parameters for deleting a workflow
     */
    export interface DeleteParams {
        /** Workflow ID */
        workflow_id: string;
    }

    /**
     * Parameters for triggering a workflow
     */
    export interface TriggerParams {
        /** Workflow ID */
        workflow_id: string;
    }
}

// Re-export workflow-related types at module level for convenience
export {
    WorkflowListResultData,
    WorkflowResultData,
    WorkflowDetailResultData,
    WorkflowNode,
    WorkflowNodeConnection,
    TriggerNode,
    ExecuteNode,
    NodePosition
};

