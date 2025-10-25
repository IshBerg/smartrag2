# SmartRAG v2 - ONNX Models

## Директория моделей

Эта директория содержит ONNX модели для генерации embeddings.

## Требуемые модели

### all-MiniLM-L6-v2.onnx

**Описание:** Sentence embedding модель от Hugging Face
**Размер:** ~23 MB
**Dimensions:** 384
**Источник:** https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2

**Как получить:**
1. Скачать модель с Hugging Face
2. Конвертировать в ONNX формат (если нужно)
3. Поместить в эту директорию

**Альтернативы:**
- all-mpnet-base-v2.onnx (768 dimensions, ~420 MB)
- paraphrase-MiniLM-L6-v2.onnx (384 dimensions, ~23 MB)

## Конвертация модели

Если модель в PyTorch формате:
```python
from sentence_transformers import SentenceTransformer
import torch

model = SentenceTransformer('all-MiniLM-L6-v2')

# Экспорт в ONNX
dummy_input = torch.randint(0, 1000, (1, 128))
torch.onnx.export(
    model,
    (dummy_input,),
    "all-MiniLM-L6-v2.onnx",
    input_names=['input_ids'],
    output_names=['embeddings'],
    dynamic_axes={'input_ids': {0: 'batch', 1: 'sequence'}}
)
```

## Примечание

На компьютере нужно:
1. Создать директорию `src/main/assets/models/`
2. Поместить туда ONNX модель
3. При сборке она автоматически упакуется в APK

## Загрузка готовой ONNX модели

Готовые ONNX модели можно найти здесь:
- Hugging Face Hub: https://huggingface.co/models?library=onnx
- Optimum library: https://github.com/huggingface/optimum

### Использование Optimum для конвертации

```bash
pip install optimum[exporters]

optimum-cli export onnx \
  --model sentence-transformers/all-MiniLM-L6-v2 \
  --task feature-extraction \
  all-MiniLM-L6-v2-onnx/
```

## Структура директории после добавления модели

```
src/main/assets/models/
├── README.md                    # Этот файл
└── all-MiniLM-L6-v2.onnx       # ONNX модель (нужно добавить)
```

## Поддерживаемые модели

SmartRAG v2 поддерживает любые BERT-подобные модели в формате ONNX с выходом в виде embeddings:
- Sentence Transformers
- BERT base/large
- DistilBERT
- RoBERTa
- MiniLM

**Важно:** При использовании другой модели обновите параметр `embeddingDimensions` в конфигурации.
