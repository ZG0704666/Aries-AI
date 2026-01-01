/**
 * Chat Manager Tool definitions for Assistance Package
 * 
 * This file provides type definitions for chat management operations,
 * including creating chats, listing chats, switching between chats, and sending messages.
 */

import {
    ChatServiceStartResultData,
    ChatCreationResultData,
    ChatListResultData,
    ChatSwitchResultData,
    MessageSendResultData
} from './results';

/**
 * Chat Manager namespace
 * Provides methods for managing chat conversations
 */
export namespace Chat {
    /**
     * Start the chat service (floating window)
     * @returns Promise resolving to service start result
     */
    function startService(): Promise<ChatServiceStartResultData>;

    /**
     * Create a new chat conversation
     * @returns Promise resolving to the new chat creation result
     */
    function createNew(): Promise<ChatCreationResultData>;

    /**
     * List all chat conversations
     * @returns Promise resolving to the list of all chats
     */
    function listAll(): Promise<ChatListResultData>;

    /**
     * Switch to a specific chat conversation
     * @param chatId - The ID of the chat to switch to
     * @returns Promise resolving to the chat switch result
     */
    function switchTo(chatId: string): Promise<ChatSwitchResultData>;

    /**
     * Send a message to the AI
     * @param message - The message content to send
     * @param chatId - Optional chat ID to send the message to (defaults to current chat)
     * @returns Promise resolving to the message send result
     */
    function sendMessage(message: string, chatId?: string): Promise<MessageSendResultData>;
}

