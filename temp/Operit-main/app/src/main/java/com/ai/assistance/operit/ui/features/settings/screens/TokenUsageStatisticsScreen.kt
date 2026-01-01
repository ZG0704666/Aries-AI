package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.model.BillingMode
import com.ai.assistance.operit.ui.components.CustomScaffold

private const val DEFAULT_INPUT_PRICE = 2.0
private const val DEFAULT_OUTPUT_PRICE = 3.0
private const val DEFAULT_CACHED_INPUT_PRICE = 0.2
private const val DEFAULT_PRICE_PER_REQUEST = 0.01

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenUsageStatisticsScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    
    // State to hold token data for all provider models
    val providerModelTokenUsage = remember { mutableStateMapOf<String, Triple<Int, Int, Int>>() }
    // State to hold request counts for all provider models
    val providerModelRequestCounts = remember { mutableStateMapOf<String, Int>() }
    // State to hold custom pricing for each model (input, output, cached input)
    val modelPricing = remember { mutableStateMapOf<String, Triple<Double, Double, Double>>() }
    // State to hold billing mode for each model
    val modelBillingMode = remember { mutableStateMapOf<String, BillingMode>() }
    // State to hold price per request for each model
    val modelPricePerRequest = remember { mutableStateMapOf<String, Double>() }
    var showPricingDialog by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    
    // Collect tokens for ALL provider models from ApiPreferences
    LaunchedEffect(Unit) {
        scope.launch {
            apiPreferences.allProviderModelTokensFlow.collect { tokensMap ->
                providerModelTokenUsage.clear()
                providerModelTokenUsage.putAll(tokensMap)
                
                // Initialize pricing and billing mode for new models with default values
                tokensMap.keys.forEach { model ->
                    if (!modelPricing.containsKey(model)) {
                        modelPricing[model] = Triple(DEFAULT_INPUT_PRICE, DEFAULT_OUTPUT_PRICE, DEFAULT_CACHED_INPUT_PRICE)
                    }
                    if (!modelBillingMode.containsKey(model)) {
                        modelBillingMode[model] = BillingMode.TOKEN
                    }
                    if (!modelPricePerRequest.containsKey(model)) {
                        modelPricePerRequest[model] = DEFAULT_PRICE_PER_REQUEST
                    }
                }
            }
        }
    }
    
    // Load request counts
    LaunchedEffect(Unit) {
        scope.launch {
            val requestCounts = apiPreferences.getAllProviderModelRequestCounts()
            providerModelRequestCounts.clear()
            providerModelRequestCounts.putAll(requestCounts)
        }
    }
    
    // Load custom pricing, billing mode, and price per request from preferences
    LaunchedEffect(Unit) {
        scope.launch {
            providerModelTokenUsage.keys.forEach { model ->
                // Load token pricing
                val inputPrice = apiPreferences.getModelInputPrice(model)
                val outputPrice = apiPreferences.getModelOutputPrice(model)
                val cachedInputPrice = apiPreferences.getModelCachedInputPrice(model)
                if (inputPrice > 0.0 || outputPrice > 0.0 || cachedInputPrice > 0.0) {
                    modelPricing[model] = Triple(inputPrice, outputPrice, cachedInputPrice)
                }
                
                // Load billing mode
                val billingMode = apiPreferences.getBillingModeForProviderModel(model)
                modelBillingMode[model] = billingMode
                
                // Load price per request
                val pricePerRequest = apiPreferences.getPricePerRequestForProviderModel(model)
                modelPricePerRequest[model] = pricePerRequest
            }
        }
    }

    // Calculate costs for each provider model based on billing mode
    val providerModelCosts = providerModelTokenUsage.mapValues { (model, tokens) ->
        val billingMode = modelBillingMode[model] ?: BillingMode.TOKEN
        
        when (billingMode) {
            BillingMode.TOKEN -> {
                val pricing = modelPricing[model] ?: Triple(DEFAULT_INPUT_PRICE, DEFAULT_OUTPUT_PRICE, DEFAULT_CACHED_INPUT_PRICE)
                // tokens.first = total input, tokens.second = output, tokens.third = cached input
                val nonCachedInput = tokens.first - tokens.third
                (nonCachedInput / 1_000_000.0 * pricing.first) + 
                (tokens.second / 1_000_000.0 * pricing.second) + 
                (tokens.third / 1_000_000.0 * pricing.third)
            }
            BillingMode.COUNT -> {
                val pricePerRequest = modelPricePerRequest[model] ?: DEFAULT_PRICE_PER_REQUEST
                val requestCount = providerModelRequestCounts[model] ?: 0
                requestCount * pricePerRequest
            }
        }
    }

    val totalInputTokens = providerModelTokenUsage.values.sumOf { it.first }
    val totalOutputTokens = providerModelTokenUsage.values.sumOf { it.second }
    val totalCachedInputTokens = providerModelTokenUsage.values.sumOf { it.third }
    val totalTokens = totalInputTokens + totalOutputTokens
    val totalRequests = providerModelRequestCounts.values.sum()

    // Calculate total cost across all models (mixed billing modes)
    val totalCost = providerModelCosts.values.sum()

    CustomScaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showResetDialog = true },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = stringResource(id = R.string.settings_reset_all_counts)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_usage_summary),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.settings_total_tokens),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$totalTokens",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(id = R.string.settings_total_requests),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$totalRequests",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(id = R.string.settings_total_cost),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "¥${String.format("%.2f", totalCost)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Token breakdown by type
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.settings_input_tokens),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$totalInputTokens",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.settings_output_tokens),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$totalOutputTokens",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            if (totalCachedInputTokens > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_cached_tokens_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = "$totalCachedInputTokens",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.settings_model_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.settings_click_to_edit_pricing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Model details
            val sortedProviderModels = providerModelTokenUsage.entries.sortedBy { it.key }
            
            if (sortedProviderModels.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(id = R.string.settings_no_token_records),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(sortedProviderModels) { (providerModel, tokens) ->
                    val (input, output, cached) = tokens
                    val cost = providerModelCosts[providerModel] ?: 0.0
                    val pricing = modelPricing[providerModel] ?: Triple(DEFAULT_INPUT_PRICE, DEFAULT_OUTPUT_PRICE, DEFAULT_CACHED_INPUT_PRICE)
                    val billingMode = modelBillingMode[providerModel] ?: BillingMode.TOKEN
                    val requestCount = providerModelRequestCounts[providerModel] ?: 0
                    val pricePerRequest = modelPricePerRequest[providerModel] ?: DEFAULT_PRICE_PER_REQUEST
                    
                    TokenUsageModelCard(
                        modelName = providerModel,
                        inputTokens = input,
                        cachedInputTokens = cached,
                        outputTokens = output,
                        requestCount = requestCount,
                        cost = cost,
                        inputPrice = pricing.first,
                        outputPrice = pricing.second,
                        billingMode = billingMode,
                        pricePerRequest = pricePerRequest,
                        onClick = {
                            selectedModel = providerModel
                            showPricingDialog = true
                        }
                    )
                }
            }
        }
    }

    // Pricing and Billing Mode Dialog
    if (showPricingDialog && selectedModel.isNotEmpty()) {
        val currentPricing = modelPricing[selectedModel] ?: Triple(DEFAULT_INPUT_PRICE, DEFAULT_OUTPUT_PRICE, DEFAULT_CACHED_INPUT_PRICE)
        val currentBillingMode = modelBillingMode[selectedModel] ?: BillingMode.TOKEN
        val currentPricePerRequest = modelPricePerRequest[selectedModel] ?: DEFAULT_PRICE_PER_REQUEST
        
        var billingMode by remember { mutableStateOf(currentBillingMode) }
        var inputPrice by remember { mutableStateOf(currentPricing.first.toString()) }
        var outputPrice by remember { mutableStateOf(currentPricing.second.toString()) }
        var cachedInputPrice by remember { mutableStateOf(currentPricing.third.toString()) }
        var pricePerRequest by remember { mutableStateOf(currentPricePerRequest.toString()) }
        
        AlertDialog(
            onDismissRequest = { showPricingDialog = false },
            title = {
                Text(text = stringResource(id = R.string.settings_edit_model_pricing, selectedModel))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Billing Mode Selection
                    Text(
                        text = stringResource(id = R.string.settings_billing_mode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = billingMode == BillingMode.TOKEN,
                            onClick = { billingMode = BillingMode.TOKEN },
                            label = { Text(stringResource(id = R.string.settings_billing_mode_token)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = billingMode == BillingMode.COUNT,
                            onClick = { billingMode = BillingMode.COUNT },
                            label = { Text(stringResource(id = R.string.settings_billing_mode_count)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    HorizontalDivider()
                    
                    // Conditional pricing inputs based on billing mode
                    if (billingMode == BillingMode.TOKEN) {
                        Text(
                            text = stringResource(id = R.string.settings_pricing_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        OutlinedTextField(
                            value = inputPrice,
                            onValueChange = { inputPrice = it },
                            label = { Text(stringResource(id = R.string.settings_input_price_per_million)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = cachedInputPrice,
                            onValueChange = { cachedInputPrice = it },
                            label = { Text(stringResource(id = R.string.settings_cached_input_price_per_million)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = outputPrice,
                            onValueChange = { outputPrice = it },
                            label = { Text(stringResource(id = R.string.settings_output_price_per_million)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "设置每次API请求的人民币价格",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        OutlinedTextField(
                            value = pricePerRequest,
                            onValueChange = { pricePerRequest = it },
                            label = { Text(stringResource(id = R.string.settings_price_per_request)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            // Save billing mode
                            modelBillingMode[selectedModel] = billingMode
                            apiPreferences.setBillingModeForProviderModel(selectedModel, billingMode)
                            
                            if (billingMode == BillingMode.TOKEN) {
                                // Save token pricing
                                val inputPriceDouble = inputPrice.toDoubleOrNull() ?: DEFAULT_INPUT_PRICE
                                val outputPriceDouble = outputPrice.toDoubleOrNull() ?: DEFAULT_OUTPUT_PRICE
                                val cachedInputPriceDouble = cachedInputPrice.toDoubleOrNull() ?: DEFAULT_CACHED_INPUT_PRICE
                                
                                modelPricing[selectedModel] = Triple(inputPriceDouble, outputPriceDouble, cachedInputPriceDouble)
                                apiPreferences.setModelInputPrice(selectedModel, inputPriceDouble)
                                apiPreferences.setModelOutputPrice(selectedModel, outputPriceDouble)
                                apiPreferences.setModelCachedInputPrice(selectedModel, cachedInputPriceDouble)
                            } else {
                                // Save per-request pricing
                                val pricePerRequestDouble = pricePerRequest.toDoubleOrNull() ?: DEFAULT_PRICE_PER_REQUEST
                                modelPricePerRequest[selectedModel] = pricePerRequestDouble
                                apiPreferences.setPricePerRequestForProviderModel(selectedModel, pricePerRequestDouble)
                            }
                        }
                        
                        showPricingDialog = false
                    }
                ) {
                    Text(stringResource(id = R.string.settings_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPricingDialog = false }
                ) {
                    Text(stringResource(id = R.string.settings_cancel))
                }
            }
        )
    }

    // Reset Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(text = stringResource(id = R.string.settings_reset_confirmation))
            },
            text = {
                Text(text = stringResource(id = R.string.settings_reset_warning))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            apiPreferences.resetAllProviderModelTokenCounts()
                            providerModelRequestCounts.clear()
                        }
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(id = R.string.settings_reset))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false }
                ) {
                    Text(stringResource(id = R.string.settings_cancel))
                }
            }
        )
    }
}

@Composable
private fun TokenUsageModelCard(
    modelName: String,
    inputTokens: Int,
    cachedInputTokens: Int,
    outputTokens: Int,
    requestCount: Int,
    cost: Double,
    inputPrice: Double,
    outputPrice: Double,
    billingMode: BillingMode,
    pricePerRequest: Double,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Billing mode indicator as a small tag
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = when (billingMode) {
                                    BillingMode.TOKEN -> stringResource(id = R.string.settings_billing_mode_token)
                                    BillingMode.COUNT -> stringResource(id = R.string.settings_billing_mode_count)
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when (billingMode) {
                                BillingMode.TOKEN -> MaterialTheme.colorScheme.secondaryContainer
                                BillingMode.COUNT -> MaterialTheme.colorScheme.tertiaryContainer
                            }
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(id = R.string.settings_edit_pricing),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Request count display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.settings_request_count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$requestCount",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.settings_input_tokens),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$inputTokens",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (cachedInputTokens > 0) {
                        Text(
                            text = stringResource(R.string.settings_cached_tokens, cachedInputTokens),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (billingMode == BillingMode.TOKEN) {
                        Text(
                            text = stringResource(id = R.string.settings_price_format, inputPrice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(id = R.string.settings_output_tokens),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$outputTokens",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (billingMode == BillingMode.TOKEN) {
                        Text(
                            text = stringResource(id = R.string.settings_price_format, outputPrice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(id = R.string.settings_total_cost),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "¥${String.format("%.2f", cost)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (billingMode == BillingMode.COUNT) {
                        Text(
                            text = stringResource(id = R.string.settings_per_request_cost, pricePerRequest),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
} 