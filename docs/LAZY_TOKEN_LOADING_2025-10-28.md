# 🎯 Правильная последовательность: Lazy Token Loading

Точно! Зачем получать токены для всех провайдеров, если в конвейере будут участвовать только 2-3? Оптимизируем процесс.

## 🔄 Последовательность выполнения

### Правильный flow работы системы:

```kotlin
class OrchestratorSystem {

    suspend fun executeTask(task: UserTask): TaskResult {

        // 1️⃣ DISCOVERY: Определяем доступные провайдеры и регионы
        val availableProviders = discoverProviders()
        println("📍 Обнаружено провайдеров: ${availableProviders.size}")

        // 2️⃣ LATENCY MAPPING: Строим карту задержек
        val latencyMap = buildLatencyMap(availableProviders)
        println("⚡ Карта задержек построена для ${latencyMap.size} регионов")

        // 3️⃣ PIPELINE BUILDING: Дирижер строит оптимальный конвейер
        val pipeline = orchestrator.buildPipeline(
            task = task,
            latencyMap = latencyMap,
            providerCatalog = availableProviders
        )
        println("🎼 Конвейер построен: ${pipeline.steps.size} этапов")

        // 4️⃣ TOKEN ACQUISITION: Получаем токены ТОЛЬКО для участников конвейера
        val requiredProviders = pipeline.getRequiredProviders()
        val tokens = acquireTokensFor(requiredProviders)
        println("🔐 Получены токены для: ${tokens.keys.joinToString()}")

        // 5️⃣ EXECUTION: Запускаем конвейер с полученными токенами
        val result = executePipeline(
            pipeline = pipeline,
            tokens = tokens,
            input = task.input
        )

        return result
    }
}
```

## 📊 Структуры данных

### 1. Карта задержек (без токенов):

```kotlin
data class LatencyMap(
    val measurements: Map<ProviderEndpoint, LatencyInfo>,
    val measurementTime: Instant,
    val userLocation: GeoLocation
)

data class ProviderEndpoint(
    val provider: ProviderType,     // AWS, Azure, Google, Oracle, Alibaba, IBM
    val region: String,              // me-south-1, israelcentral, etc.
    val endpoint: String,            // https://bedrock.me-south-1.amazonaws.com
    val capabilities: Set<Capability> // Что умеет этот endpoint
)

data class LatencyInfo(
    val ping: Long,                  // Простой ICMP/HTTP ping в мс
    val isAvailable: Boolean,        // Доступен ли вообще
    val confidence: Float            // Уверенность в измерении (0.0-1.0)
)
```

### 2. Конвейер (план выполнения):

```kotlin
data class Pipeline(
    val id: String = UUID.randomUUID().toString(),
    val steps: List<PipelineStep>,
    val fallbackStrategies: Map<String, FallbackStrategy>
)

data class PipelineStep(
    val name: String,                   // "analyze_image"
    val provider: ProviderEndpoint,     // Конкретный endpoint
    val model: String,                   // "gemini-1.5-flash"
    val input: DataType,                // IMAGE
    val output: DataType,               // TEXT
    val estimatedLatency: Long,         // Прогноз задержки
    val estimatedCost: Float,           // Прогноз стоимости
    val fallbackProviders: List<ProviderEndpoint> // Резервные варианты
)
```

### 3. Токены (получаем только для нужных):

```kotlin
data class TokenSet(
    val tokens: Map<ProviderType, TemporaryCredentials>
)

sealed class TemporaryCredentials {
    data class AWS_STS(
        val accessKeyId: String,
        val secretAccessKey: String,
        val sessionToken: String,
        val expiration: Instant
    ) : TemporaryCredentials()

    data class Azure_Bearer(
        val accessToken: String,
        val expiresOn: Instant
    ) : TemporaryCredentials()

    data class Google_OAuth(
        val accessToken: String,
        val expiresAt: Instant
    ) : TemporaryCredentials()

    data class Oracle_Delegation(
        val token: String,
        val expiresAt: Instant
    ) : TemporaryCredentials()

    data class Alibaba_STS(
        val accessKeyId: String,
        val accessKeySecret: String,
        val stsToken: String,
        val expiration: Instant
    ) : TemporaryCredentials()

    data class IBM_Bearer(
        val token: String,
        val expiresIn: Long
    ) : TemporaryCredentials()
}
```

## 🎼 Дирижер строит конвейер

### Логика построения без токенов:

```kotlin
class PipelineOrchestrator {

    fun buildPipeline(
        task: UserTask,
        latencyMap: LatencyMap,
        providerCatalog: List<ProviderEndpoint>
    ): Pipeline {

        // Анализируем задачу
        val taskAnalysis = analyzeTask(task)

        // Определяем необходимые шаги
        val requiredSteps = determineSteps(taskAnalysis)

        // Для каждого шага выбираем оптимального провайдера
        val pipelineSteps = requiredSteps.map { step ->
            selectOptimalProvider(
                step = step,
                latencyMap = latencyMap,
                catalog = providerCatalog,
                constraints = task.constraints
            )
        }

        return Pipeline(
            steps = pipelineSteps,
            fallbackStrategies = generateFallbackStrategies(pipelineSteps)
        )
    }

    private fun selectOptimalProvider(
        step: RequiredStep,
        latencyMap: LatencyMap,
        catalog: List<ProviderEndpoint>,
        constraints: Constraints
    ): PipelineStep {

        // Фильтруем провайдеров по возможностям
        val capable = catalog.filter { provider ->
            provider.capabilities.contains(step.requiredCapability)
        }

        // Сортируем по критериям
        val sorted = when (constraints.optimization) {
            Optimization.SPEED -> {
                capable.sortedBy { latencyMap.measurements[it]?.ping ?: Long.MAX_VALUE }
            }
            Optimization.COST -> {
                capable.sortedBy { getCostEstimate(it, step) }
            }
            Optimization.QUALITY -> {
                capable.sortedByDescending { getQualityScore(it, step) }
            }
        }

        val selected = sorted.first()

        return PipelineStep(
            name = step.name,
            provider = selected,
            model = selectModel(selected, step),
            input = step.inputType,
            output = step.outputType,
            estimatedLatency = latencyMap.measurements[selected]?.ping ?: 0,
            estimatedCost = getCostEstimate(selected, step),
            fallbackProviders = sorted.drop(1).take(2) // Берем 2 резервных
        )
    }
}
```

## 🔐 Получение токенов только для участников

### Lazy token acquisition:

```kotlin
class TokenAcquisitionService {

    suspend fun acquireTokensFor(
        providers: Set<ProviderEndpoint>
    ): TokenSet {

        // Группируем по типу провайдера (AWS может быть в разных регионах)
        val groupedByType = providers.groupBy { it.provider }

        // Параллельно получаем токены для каждого уникального провайдера
        val tokens = coroutineScope {
            groupedByType.map { (providerType, endpoints) ->
                async {
                    val credentials = acquireTokenForProvider(providerType, endpoints)
                    providerType to credentials
                }
            }.awaitAll().toMap()
        }

        println("✅ Получены токены для: ${tokens.keys.joinToString()}")

        return TokenSet(tokens)
    }

    private suspend fun acquireTokenForProvider(
        provider: ProviderType,
        endpoints: List<ProviderEndpoint>
    ): TemporaryCredentials {

        return when (provider) {
            ProviderType.AWS -> {
                // AWS STS AssumeRole
                val stsClient = createSTSClient(endpoints.first().region)
                val response = stsClient.assumeRole(
                    roleArn = getConfiguredRole(provider),
                    sessionName = "pipeline-${UUID.randomUUID()}",
                    duration = 3600
                )
                AWS_STS(
                    accessKeyId = response.accessKeyId,
                    secretAccessKey = response.secretAccessKey,
                    sessionToken = response.sessionToken,
                    expiration = response.expiration
                )
            }

            ProviderType.AZURE -> {
                // Azure Managed Identity
                val credential = DefaultAzureCredentialBuilder().build()
                val token = credential.getToken(
                    TokenRequestContext().addScopes("https://cognitiveservices.azure.com/.default")
                ).block()
                Azure_Bearer(
                    accessToken = token.token,
                    expiresOn = token.expiresAt.toInstant()
                )
            }

            ProviderType.GOOGLE -> {
                // Google Service Account
                val credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped("https://www.googleapis.com/auth/cloud-platform")
                credentials.refreshIfExpired()
                Google_OAuth(
                    accessToken = credentials.accessToken.tokenValue,
                    expiresAt = Instant.ofEpochMilli(credentials.accessToken.expirationTimeMillis)
                )
            }

            // ... аналогично для Oracle, Alibaba, IBM
        }
    }
}
```

## 🚀 Выполнение конвейера

### Запуск с полученными токенами:

```kotlin
class PipelineExecutor {

    suspend fun executePipeline(
        pipeline: Pipeline,
        tokens: TokenSet,
        input: Any
    ): PipelineResult {

        var currentData = input
        val stepResults = mutableListOf<StepResult>()

        for (step in pipeline.steps) {
            try {
                // Получаем токен для текущего провайдера
                val token = tokens.tokens[step.provider.provider]
                    ?: throw NoTokenException(step.provider.provider)

                // Выполняем шаг
                val result = executeStep(
                    step = step,
                    token = token,
                    input = currentData
                )

                stepResults.add(result)
                currentData = result.output

            } catch (e: Exception) {
                // Пробуем fallback провайдеров
                val fallbackResult = tryFallbacks(
                    step = step,
                    tokens = tokens,
                    input = currentData,
                    originalError = e
                )

                if (fallbackResult != null) {
                    stepResults.add(fallbackResult)
                    currentData = fallbackResult.output
                } else {
                    throw PipelineExecutionException(
                        "Шаг ${step.name} не выполнен: ${e.message}"
                    )
                }
            }
        }

        return PipelineResult(
            output = currentData,
            steps = stepResults,
            totalLatency = stepResults.sumOf { it.latency },
            totalCost = stepResults.sumOf { it.cost }
        )
    }
}
```

## 💡 Преимущества подхода

1. **Эффективность** - токены только для используемых провайдеров
2. **Безопасность** - токены живут минимальное время
3. **Гибкость** - конвейер адаптируется под задачу
4. **Экономия** - не тратим квоты на получение ненужных токенов
5. **Скорость** - параллельное получение токенов

## 🔄 Правильная последовательность

**Правильно:**
```
Discover → Measure → Build → Acquire → Execute
```

**Неправильно:**
```
Discover → Acquire All → Measure → Build → Execute
```

## 📋 Ключевые концепции

### 1. Discovery (Обнаружение)
- Сканирование доступных провайдеров
- Определение capabilities каждого endpoint
- Построение каталога провайдеров

### 2. Latency Mapping (Карта задержек)
- Измерение ping до каждого endpoint
- Определение доступности
- Построение карты задержек БЕЗ токенов

### 3. Pipeline Building (Построение конвейера)
- Анализ задачи пользователя
- Выбор оптимальных провайдеров
- Создание плана выполнения с fallback стратегиями

### 4. Token Acquisition (Получение токенов)
- **LAZY** - только для участников конвейера
- Параллельное получение для разных провайдеров
- Минимальное время жизни токенов

### 5. Execution (Выполнение)
- Последовательное выполнение шагов
- Автоматический fallback при ошибках
- Сбор метрик (latency, cost)

## 🎯 Применение в YAKKI SMART

### Translation Pipeline Example:

```kotlin
// Задача: Перевести речь с русского на английский
val task = UserTask(
    type = TaskType.TRANSLATION,
    input = AudioInput(language = "ru"),
    output = AudioOutput(language = "en"),
    constraints = Constraints(
        optimization = Optimization.SPEED,
        maxLatency = 500, // миллисекунд
        maxCost = 0.05    // долларов
    )
)

// Система построит конвейер:
// 1. STT (Speech-to-Text): Google Cloud Speech (Israel Central) - 45ms
// 2. Translation: DeepL API (Frankfurt) - 120ms
// 3. TTS (Text-to-Speech): PlayHT (Tel Aviv) - 300ms
//
// Токены будут получены ТОЛЬКО для:
// - Google Cloud (OAuth)
// - DeepL (API Key)
// - PlayHT (Bearer Token)
//
// AWS, Azure, Oracle - токены НЕ запрашиваются
```

## 🔐 Безопасность

### Temporary Credentials Best Practices:

1. **Минимальное время жизни**
   - AWS STS: 1 час (3600 секунд)
   - Azure Bearer: 1 час
   - Google OAuth: 1 час

2. **Минимальные права**
   - Только необходимые API endpoints
   - Read-only где возможно
   - Принцип least privilege

3. **Автоматическое обновление**
   - Проверка expiration перед каждым использованием
   - Автоматический refresh при необходимости
   - Fallback на новый токен при ошибке 401/403

4. **Безопасное хранение**
   - В памяти (не на диске)
   - Encrypted если необходимо персистить
   - Очистка после завершения задачи

## 📈 Метрики и мониторинг

### Что измеряем:

```kotlin
data class PipelineMetrics(
    val totalLatency: Long,           // Общая задержка
    val totalCost: Float,             // Общая стоимость
    val tokenAcquisitionTime: Long,   // Время получения токенов
    val executionTime: Long,          // Время выполнения
    val fallbacksUsed: Int,           // Сколько раз использовали fallback
    val providersUsed: List<ProviderType> // Какие провайдеры участвовали
)
```

### Оптимизация на основе метрик:

- Если token acquisition > 10% от total latency → кэшировать токены
- Если fallbacks > 20% → пересмотреть выбор primary провайдеров
- Если cost > budget → переключиться на более дешевых провайдеров

---

## 🚀 Roadmap

### Phase 1: Basic Implementation
- ✅ Discovery service
- ✅ Latency mapping
- ⏳ Pipeline orchestrator
- ⏳ Token acquisition service
- ⏳ Pipeline executor

### Phase 2: Advanced Features
- Token caching with TTL
- Predictive pipeline building (ML-based)
- Multi-region fallback strategies
- Cost optimization algorithms

### Phase 3: Production Features
- Distributed tracing
- Real-time monitoring dashboard
- A/B testing for provider selection
- Auto-scaling based on demand

---

*This document captures the Lazy Token Loading architecture discussed on October 28, 2025. It defines the optimal sequence for multi-cloud AI orchestration with efficient credential management.*
