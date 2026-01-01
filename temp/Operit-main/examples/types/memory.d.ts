/**
 * Memory management namespace
 * Provides methods for managing the memory library
 */

/**
 * Memory management tools
 */
export namespace Memory {
    /**
     * Query the memory library
     * @param query - Search query
     * @param folderPath - Optional folder path to search within
     * @param threshold - Optional semantic similarity threshold (0.0-1.0, default 0.35)
     * @param limit - Optional maximum number of results (1-20, default 5)
     * @returns Query results as a string
     */
    function query(query: string, folderPath?: string, threshold?: number, limit?: number): Promise<string>;

    /**
     * Get a memory by exact title
     * @param title - The exact title of the memory
     * @param chunkIndex - Optional chunk index for document nodes
     * @param chunkRange - Optional chunk range for document nodes (e.g., "3-7")
     * @param query - Optional query to search within the document
     * @returns Memory content as a string
     */
    function getByTitle(title: string, chunkIndex?: number, chunkRange?: string, query?: string): Promise<string>;

    /**
     * Create a new memory
     * @param title - Memory title
     * @param content - Memory content
     * @param contentType - Optional content type (default "text/plain")
     * @param source - Optional source (default "ai_created")
     * @param folderPath - Optional folder path (default "")
     * @returns Creation result as a string
     */
    function create(title: string, content: string, contentType?: string, source?: string, folderPath?: string): Promise<string>;

    /**
     * Update options for memory update
     */
    interface UpdateOptions {
        newTitle?: string;
        content?: string;
        contentType?: string;
        source?: string;
        credibility?: number;
        importance?: number;
        folderPath?: string;
        tags?: string;
    }

    /**
     * Update an existing memory
     * @param oldTitle - The current title of the memory
     * @param updates - Update options
     * @returns Update result as a string
     */
    function update(oldTitle: string, updates?: UpdateOptions): Promise<string>;

    /**
     * Delete a memory
     * @param title - The title of the memory to delete
     * @returns Deletion result as a string
     */
    function deleteMemory(title: string): Promise<string>;

    /**
     * Create a link between two memories
     * @param sourceTitle - The title of the source memory
     * @param targetTitle - The title of the target memory
     * @param linkType - Optional link type (default "related")
     * @param weight - Optional link strength (0.0-1.0, default 0.7)
     * @param description - Optional description of the relationship
     * @returns Link creation result
     */
    function link(sourceTitle: string, targetTitle: string, linkType?: string, weight?: number, description?: string): Promise<import('./results').MemoryLinkResultData>;
}

