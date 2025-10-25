# PROTOCOL.md v1.0 - SmartRAG v2 Development Protocol

## Соглашения по коду

### Заголовки файлов
```kotlin
/**
 * SmartRAG v2 - [Название компонента]
 * Version: 2.0.0
 * Date: 2025-10-26
 * Author: YAKKI SMART Team
 */
```

### Качество кода
- НЕ использовать заглушки (TODO, NotImplementedError)
- Каждый метод полностью реализован
- KDoc комментарии для публичных API
- Обрабатывать ошибки через Result/sealed classes

### Структура Kotlin класса
```kotlin
class Component {
    companion object { }      // 1. Companion
    private val property      // 2. Свойства
    init { }                  // 3. init
    fun publicMethod() { }    // 4. Публичные методы
    private fun helper() { }  // 5. Приватные методы
}
```

## Архитектурные принципы

### Разделение ответственности
- **SQLite**: хранилище данных, граф, FTS5
- **Rust**: векторный поиск (Hora)
- **Kotlin**: API и координация

### Коммуникация через ID
- SQLite генерирует ID
- Rust хранит (vector, id) пары
- Поиск: Rust → ID → SQLite достает данные

### Производительность
- Batch операции (минимум 100 записей)
- Корутины для I/O
- Кэширование частых запросов

### Обработка ошибок
```kotlin
sealed class SmartRAGError {
    data class DatabaseError(val message: String) : SmartRAGError()
    data class VectorSearchError(val cause: Throwable) : SmartRAGError()
}

fun search(query: String): Result<SearchResult>
```
