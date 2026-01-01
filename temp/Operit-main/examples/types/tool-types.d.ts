/**
 * Tool name type definitions for Assistance Package Tools
 * 
 * This file defines the available tool names and maps them to their result types.
 */

import {
    DirectoryListingData, FileContentData, BinaryFileContentData, FileOperationData, FileExistsData,
    FindFilesResultData, FileInfoData, FileConversionResultData, FileFormatConversionsResultData,
    HttpResponseData, VisitWebResultData,
    SleepResultData, SystemSettingData, AppOperationData, AppListData,
    DeviceInfoResultData, NotificationData, LocationData,
    UIPageResultData, UIActionResultData, CombinedOperationResultData, AutomationExecutionResultData,
    CalculationResultData, FFmpegResultData, ADBResultData, IntentResultData, TerminalCommandResultData,
    FilePartContentData, FileApplyResultData, WorkflowListResultData, WorkflowResultData, WorkflowDetailResultData,
    StringResultData, ChatServiceStartResultData, ChatCreationResultData, ChatListResultData,
    ChatSwitchResultData, MessageSendResultData, MemoryLinkResultData, GrepResultData
} from './results';

/**
 * Maps tool names to their result data types
 */
export interface ToolResultMap {
    // File operations
    'list_files': DirectoryListingData;
    'read_file': FileContentData;
    'read_file_part': FilePartContentData;
    'read_file_full': FileContentData;
    'read_file_binary': BinaryFileContentData;

    'write_file': FileOperationData;
    'delete_file': FileOperationData;
    'file_exists': FileExistsData;
    'move_file': FileOperationData;
    'copy_file': FileOperationData;
    'make_directory': FileOperationData;
    'find_files': FindFilesResultData;
    'grep_code': GrepResultData;
    'grep_context': GrepResultData;
    'file_info': FileInfoData;
    'zip_files': FileOperationData;
    'unzip_files': FileOperationData;
    'open_file': FileOperationData;
    'share_file': FileOperationData;
    'download_file': FileOperationData;
    'apply_file': FileApplyResultData;

    // Network operations
    'http_request': HttpResponseData;
    'visit_web': VisitWebResultData;
    'multipart_request': HttpResponseData;
    'manage_cookies': HttpResponseData;

    // System operations
    'sleep': SleepResultData;
    'get_system_setting': SystemSettingData;
    'modify_system_setting': SystemSettingData;
    'install_app': AppOperationData;
    'uninstall_app': AppOperationData;
    'list_installed_apps': AppListData;
    'start_app': AppOperationData;
    'stop_app': AppOperationData;
    'device_info': DeviceInfoResultData;
    'get_notifications': NotificationData;
    'get_device_location': LocationData;
    'trigger_tasker_event': string;

    // UI operations
    'get_page_info': UIPageResultData;
    'click_element': UIActionResultData;
    'tap': UIActionResultData;
    'set_input_text': UIActionResultData;
    'press_key': UIActionResultData;
    'swipe': UIActionResultData;
    'combined_operation': CombinedOperationResultData;
    'run_ui_subagent': AutomationExecutionResultData;

    // Calculator operations
    'calculate': CalculationResultData;

    // Package operations
    'use_package': string;
    'query_memory': string;

    // FFmpeg operations
    'ffmpeg_execute': FFmpegResultData;
    'ffmpeg_info': FFmpegResultData;
    'ffmpeg_convert': FFmpegResultData;

    // ADB operations
    'execute_shell': ADBResultData;

    // Intent operations
    'execute_intent': IntentResultData;

    // Terminal operations
    'execute_terminal': TerminalCommandResultData;

    // Workflow operations
    'get_all_workflows': WorkflowListResultData;
    'create_workflow': WorkflowDetailResultData;
    'get_workflow': WorkflowDetailResultData;
    'update_workflow': WorkflowDetailResultData;
    'delete_workflow': StringResultData;
    'trigger_workflow': StringResultData;

    // Chat Manager operations
    'start_chat_service': ChatServiceStartResultData;
    'create_new_chat': ChatCreationResultData;
    'list_chats': ChatListResultData;
    'switch_chat': ChatSwitchResultData;
    'send_message_to_ai': MessageSendResultData;

    // Memory operations
    'link_memories': MemoryLinkResultData;
} 