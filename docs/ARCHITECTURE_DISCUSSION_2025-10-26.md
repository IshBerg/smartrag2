# YAKKI SMART - Architecture Discussion
## Date: October 26, 2025

---

## 📱 CORE CONCEPT: "Unlock Your Phone"

### Philosophy
[Концепция разблокировки возможностей флагманских телефонов]
- Проблема: $1000 телефон используется на 5%
- Решение: YAKKI показывает ЧТО умеет телефон
- Gamification: Phone Potential Score

### Target Audience
- Владельцы флагманов (Pixel, Samsung, iPhone)
- Power users
- Профессионалы (лекторы, гиды, бизнес)

---

## 🏗️ MODULAR ARCHITECTURE

### LLM Orchestrator
- Облачный старт (Qwen via Together.ai)
- Планируется переход на локальный
- Function calling для управления модулями
- Выбор провайдеров на основе tier/budget

### Core Modules (MVP v1.0)
1. **Translation Module** (ПРИОРИТЕТ #1)
   - STT: Google Cloud / Whisper API
   - Translation: Google Translate / DeepL / LLM-based
   - TTS: Google Cloud / PlayHT / ElevenLabs
   - Streaming vs Batch подходы

2. **Email Module** (Gmail API)
   - OAuth 2.0 интеграция
   - Чтение + отправка
   - AI композиция писем
   - SmartRAG индексация

3. **SMS Module**
   - ContentProvider (native Android)
   - BroadcastReceiver для real-time
   - SmartRAG индексация

4. **Bluetooth Module**
   - Audio routing (гарнитура/speaker)
   - "Рация" режим для переводов
   - Push-to-talk mechanism

5. **Calendar Module** (Google Calendar API)
   - Чтение событий
   - Создание/редактирование
   - Поиск свободных слотов

6. **Contacts Module** (Android native)
   - Чтение контактов
   - Semantic search via SmartRAG
   - Создание/редактирование

7. **SmartRAG v2 Integration**
   - Универсальная индексация
   - Контекст для LLM
   - Semantic search

---

## 🎧 BLUETOOTH "РАЦИЯ" CONCEPT

### Преимущества режима рации:
- Технически проще (без mixer)
- Естественный UX (привычка к рациям)
- Учит слушать собеседника
- Лучшее качество AI (один голос)
- Меньшая задержка

### Сценарии:
1. **Гарнитура + Speaker телефона**
   - Человек 1 → гарнитура mic → AI → speaker телефона → Человек 2
   - Человек 2 → mic телефона → AI → гарнитура → Человек 1

2. **Гарнитура + Колонка** (лекции)
   - Лектор → гарнитура → AI перевод → колонка для аудитории

3. **Две гарнитуры** (v2.0)
   - Требует audio mixer
   - Приватные беседы

---

## ⚡ TRANSLATION STRATEGY

### Streaming vs Batch

**Streaming (Real-time):**
- Latency: <500ms
- Cost: ~$2.50/час (Google Cloud)
- Use cases: Живые разговоры, встречи

**Batch (Whisper):**
- Latency: 2-5 секунд
- Cost: ~$1.42/час
- Use cases: Голосовые сообщения, записи

### Provider Matrix:

| Provider | Type | Latency | Cost | Quality |
|----------|------|---------|------|---------|
| Android Native | STT/TTS | Batch | FREE | Low |
| Google Cloud | STT/TTS | Streaming | Cheap | Medium |
| Whisper API | STT | Batch | Cheap | High |
| Deepgram | STT | Streaming | Medium | High |
| DeepL | Translation | - | Cheap | High |
| ElevenLabs | TTS | Streaming | Premium | Premium |
| PlayHT | TTS | Streaming | Medium | High |

---

## 💰 MONETIZATION STRATEGY

### Tiers:

**FREE:**
- 30 min/месяц
- Android native (offline)
- Google Translate basic
- Batch only

**BASIC ($4.99/мес):**
- 300 min/месяц
- Google Cloud streaming
- Real-time режим

**PREMIUM ($19.99/мес):**
- Unlimited
- Deepgram STT
- DeepL/LLM Translation
- PlayHT/ElevenLabs TTS
- SmartRAG context

**Pay-as-you-go:**
- $0.15/минута
- Premium качество
- Для разовых событий

---

## 📚 INTEGRATION ROADMAP

### MVP v1.0:
- ✅ Translation (STT → Translate → TTS)
- ✅ Bluetooth "рация"
- ✅ Email (Gmail API)
- ✅ SMS чтение
- ✅ Calendar
- ✅ Contacts
- ✅ SmartRAG v2 индексация

### v1.1:
- ⭐ Telegram integration (TDLib)
- ⭐ WhatsApp manual import
- ⭐ Bluetooth: гарнитура + колонка
- ⭐ Offline translation

### v2.0:
- ⭐ Bluetooth: 2 гарнитуры (mixer)
- ⭐ Local LLM on-device
- ⭐ IMAP/POP3 (другие почты)
- ⭐ Real-time Telegram sync

---

## 🎯 KEY ARCHITECTURAL DECISIONS

### 1. Modularity First
- Каждая capability = отдельный модуль
- LLM Orchestrator управляет всем
- Легко добавлять новые модули

### 2. Cloud → Local Evolution
- Старт: облачная LLM (Together.ai)
- Будущее: on-device Qwen
- Fallback всегда доступен

### 3. Pragmatic Approach
- "Гладко было на бумаге, забыли про овраги"
- Решать проблемы по мере поступления
- Измерять реальные метрики
- Итерировать быстро

### 4. Privacy & Security
- Локальное хранение (SQLite, encrypted)
- Минимум данных в облако
- Прозрачность для пользователя
- Opt-in для permissions

---

## 📊 TECHNICAL STACK

### Current:
- Language: Kotlin 2.2.20
- UI: Jetpack Compose + Material 3
- DI: Koin
- Network: Retrofit + OkHttp
- LLM: Together.ai (Qwen)
- Database: Room
- RAG: SmartRAG v2 (custom)

### Dependencies:
```kotlin
// Gmail/Calendar
implementation("com.google.apis:google-api-services-gmail")
implementation("com.google.apis:google-api-services-calendar")

// Translation
implementation("com.google.cloud:google-cloud-speech")
implementation("com.google.cloud:google-cloud-translate")
implementation("com.google.cloud:google-cloud-texttospeech")

// SmartRAG v2
implementation(project(":smartrag-v2"))
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")
```

---

## 🚀 NEXT STEPS

### Immediate (Computer, 5 min):
1. Clean build directories
2. Gradle Sync → Success
3. Run on emulator
4. Basic UI test

### Short-term (1-2 weeks):
1. Implement Gmail module
2. Implement SMS module
3. Basic Translation pipeline
4. SmartRAG indexing
5. Simple orchestrator

### Medium-term (1 month):
1. Multi-provider translation
2. Bluetooth audio routing
3. Calendar/Contacts integration
4. Tiered pricing implementation

### Long-term (3 months):
1. Google Play submission
2. Telegram integration
3. On-device LLM
4. Advanced features

---

## 💡 LESSONS LEARNED

1. **"Туризм vs Иммиграция"**
   - Не строим всё сразу
   - Сначала пройти Google Play
   - Потом расширять functionality

2. **"По ходу реализации"**
   - Теория ≠ практика
   - Тестировать на реальных данных
   - Измерять, оптимизировать, итерировать

3. **"Phone Potential Score"**
   - Уникальный подход к AI ассистентам
   - Фокус на разблокировке возможностей
   - Gamification вовлечённости

---

## 📝 OPEN QUESTIONS (to resolve empirically)

1. **LLM Orchestrator мощность:**
   - Начать с Qwen 7B
   - Измерить latency/accuracy/cost
   - Оптимизировать по факту

2. **Translation latency:**
   - Whisper batch: приемлема ли задержка?
   - Streaming: стоит ли cost?
   - Пользовательские предпочтения?

3. **Bluetooth audio mixer:**
   - Нужен ли для MVP?
   - Техническая сложность?
   - Альтернативы?

4. **Google Play approval:**
   - SMS permission: v1.0 или v1.1?
   - Privacy Policy детали?
   - Submission strategy?

---

## 🎉 TODAY'S ACHIEVEMENTS

### SmartRAG v2:
- ✅ 7 фаз реализованы
- ✅ 37 файлов, 4024 строк
- ✅ 101 тест
- ✅ На GitHub

### YAKKI SMART:
- ✅ 222 файла на GitHub
- ✅ SmartRAG v2 интегрирован
- ✅ Критические проблемы исправлены
- ✅ 15+ коммитов
- ✅ Ветки почищены

### Architecture:
- ✅ Модульная структура определена
- ✅ Приоритеты понятны
- ✅ Roadmap набросан
- ✅ Концепция "Unlock Your Phone"

---

*This document captures the key architectural decisions and concepts discussed on October 26, 2025. It serves as a reference for implementation and future planning.*
