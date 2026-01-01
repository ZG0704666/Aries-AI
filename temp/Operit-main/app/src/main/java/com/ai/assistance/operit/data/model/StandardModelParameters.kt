package com.ai.assistance.operit.data.model

/**
 * A data class to hold the static definition of a model parameter.
 * This serves as a single source of truth for standard parameters.
 *
 * @param T The underlying data type of the parameter's value.
 * @property id Unique identifier for the parameter.
 * @property name Default, non-localized name of the parameter.
 * @property apiName The name used in the API request body.
 * @property description Default, non-localized description of the parameter.
 * @property defaultValue The default value for the parameter.
 * @property valueType The type of the parameter's value (e.g., INT, FLOAT).
 * @property category The category the parameter belongs to.
 * @property minValue Optional minimum allowed value.
 * @property maxValue Optional maximum allowed value.
 */
data class ParameterDefinition<T : Any>(
    val id: String,
    val name: String,
    val apiName: String,
    val description: String,
    val defaultValue: T,
    val valueType: ParameterValueType,
    val category: ParameterCategory,
    val minValue: T? = null,
    val maxValue: T? = null
)

/**
 * A central repository for the definitions of all standard model parameters.
 */
object StandardModelParameters {
    // Default values for standard model parameters
    const val DEFAULT_MAX_TOKENS = 4096
    const val DEFAULT_TEMPERATURE = 1.0f
    const val DEFAULT_TOP_P = 1.0f
    const val DEFAULT_TOP_K = 0
    const val DEFAULT_PRESENCE_PENALTY = 0.0f
    const val DEFAULT_FREQUENCY_PENALTY = 0.0f
    const val DEFAULT_REPETITION_PENALTY = 1.0f

    val DEFINITIONS: List<ParameterDefinition<*>> =
        listOf(
            ParameterDefinition(
                id = "max_tokens",
                name = "最大生成Token数",
                apiName = "max_tokens",
                description = "控制AI每次最多生成的Token数量",
                defaultValue = DEFAULT_MAX_TOKENS,
                valueType = ParameterValueType.INT,
                category = ParameterCategory.GENERATION,
                minValue = 1,
                maxValue = 16000
            ),
            ParameterDefinition(
                id = "temperature",
                name = "温度",
                apiName = "temperature",
                description = "控制输出的随机性。较低的值更确定性，较高的值更随机",
                defaultValue = DEFAULT_TEMPERATURE,
                valueType = ParameterValueType.FLOAT,
                category = ParameterCategory.CREATIVITY,
                minValue = 0.0f,
                maxValue = 2.0f
            ),
            ParameterDefinition(
                id = "top_p",
                name = "Top-P 采样",
                apiName = "top_p",
                description = "作为温度的替代方案，模型仅考虑概率最高的Top-P比例的token",
                defaultValue = DEFAULT_TOP_P,
                valueType = ParameterValueType.FLOAT,
                category = ParameterCategory.CREATIVITY,
                minValue = 0.0f,
                maxValue = 1.0f
            ),
            ParameterDefinition(
                id = "top_k",
                name = "Top-K 采样",
                apiName = "top_k",
                description = "模型仅考虑概率最高的K个token。0表示禁用",
                defaultValue = DEFAULT_TOP_K,
                valueType = ParameterValueType.INT,
                category = ParameterCategory.CREATIVITY,
                minValue = 0,
                maxValue = 100
            ),
            ParameterDefinition(
                id = "presence_penalty",
                name = "存在惩罚",
                apiName = "presence_penalty",
                description = "增强模型谈论新主题的倾向。值越高，惩罚越大",
                defaultValue = DEFAULT_PRESENCE_PENALTY,
                valueType = ParameterValueType.FLOAT,
                category = ParameterCategory.REPETITION,
                minValue = -2.0f,
                maxValue = 2.0f
            ),
            ParameterDefinition(
                id = "frequency_penalty",
                name = "频率惩罚",
                apiName = "frequency_penalty",
                description = "减少模型重复同一词语的可能性。值越高，惩罚越大",
                defaultValue = DEFAULT_FREQUENCY_PENALTY,
                valueType = ParameterValueType.FLOAT,
                category = ParameterCategory.REPETITION,
                minValue = -2.0f,
                maxValue = 2.0f
            ),
            ParameterDefinition(
                id = "repetition_penalty",
                name = "重复惩罚",
                apiName = "repetition_penalty",
                description = "进一步减少重复。1.0表示不惩罚，大于1.0会降低重复可能性",
                defaultValue = DEFAULT_REPETITION_PENALTY,
                valueType = ParameterValueType.FLOAT,
                category = ParameterCategory.REPETITION,
                minValue = 0.0f,
                maxValue = 2.0f
            )
        )
} 