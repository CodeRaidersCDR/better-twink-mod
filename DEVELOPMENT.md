# Better Twink Development Guide

## Структура проекта

```
src/
├── main/
│   ├── java/
│   │   └── com/minemods/bettertwink/
│   │       ├── BetterTwinkMod.java           # Главный класс мода
│   │       ├── config/                       # Конфигурация (ForgeConfigSpec)
│   │       ├── client/
│   │       │   ├── events/                   # Client event handlers
│   │       │   ├── gui/                      # GUI Screens
│   │       │   ├── render/                   # Rendering and visualization
│   │       │   └── storage/                  # Persistence (saving/loading)
│   │       ├── data/                         # Data classes for configuration
│   │       ├── sorting/                      # Item sorting logic
│   │       ├── crafting/                     # Crafting system
│   │       ├── pathfinding/                  # Navigation and path finding
│   │       └── util/                         # Utility functions
│   └── resources/
│       ├── assets/bettertwink/
│       │   └── lang/                         # Language files (JSON)
│       ├── pack.mcmeta
│       └── META-INF/
│           └── mods.toml                     # Mod metadata
```

## Ключевые компоненты

### 1. **Configuration System** (`config/`, `data/`)
- `BetterTwinkConfig` - Forge config spec
- `ChestConfiguration` - Одна конфигурация сундука
- `ServerConfiguration` - Конфигурация для одного сервера
- `ConfigurationManager` - Глобальный менеджер конфигураций
- `ConfigurationPersistence` - Сохранение/загрузка на диск

### 2. **GUI System** (`client/gui/`)
- `BetterTwinkSettingsScreen` - Главное меню настроек
- `ChestDetailScreen` - Редактирование одного сундука
- `ModFilterScreen` - Выбор модов для фильтра
- `ItemFilterScreen` - Выбор предметов для фильтра
- `CraftingRulesScreen` - Управление рецептами крафта

### 3. **Sorting System** (`sorting/`)
- `ItemSortingEngine` - Основная логика анализа и сортировки
- `ChestInventoryManager` - Управление инвентарем сундуков
- `ItemSortingEngine.SortingTask` - Представление одной задачи сортировки

### 4. **Crafting** (`crafting/`)
- `CraftingManager` - Управление крафтом

### 5. **Navigation** (`pathfinding/`)
- `PathFinder` - A* алгоритм для поиска пути между сундуками

### 6. **Event Handlers** (`client/events/`)
- `ClientEventHandler` - Обработчик Input событий
- `SortingEventHandler` - Логика сортировки при открытии сундуков

### 7. **Rendering** (`client/render/`)
- `ChestVisualizationRenderer` - Подсветка выбранных сундуков

## Разработка новых функций

### Добавление нового фильтра типа
1. Создать класс в `data/` для представления конфигурации
2. Добавить поле в `ChestConfiguration`
3. Создать UI экран в `client/gui/`
4. Обновить методы сохранения/загрузки в соответствующих классах
5. Обновить `ItemSortingEngine.findTargetChest()` для учета нового фильтра

### Добавление визуализации
1. Создать новый класс renderer в `client/render/`
2. Подписать на необходимый event
3. Использовать PoseStack и VertexConsumer для рисования

### Оптимизация производительности
- Кэшировать результаты поиска пути
- Ограничивать частоту проверок сортировки
- Использовать пулинг для объектов которые часто создаются

## Event System

### Используемые события
- `InputEvent.Key` - обработка нажатия клавиш
- `ScreenEvent.Init.Post` - инициализация экранов
- `RenderLevelStageEvent` - рендеринг на уровне мира

## Configuration System

### Пример добавления нового конфига
```java
public static final ForgeConfigSpec.BooleanValue NEW_FEATURE = BUILDER
    .comment("Description of the feature")
    .define("newFeature", true);
```

## NBT Serialization

Все конфигурации сохраняются через NBT систему Minecraft:
- `ChestConfiguration.serializeNBT()` - сохранение сундука
- `ServerConfiguration.serializeNBT()` - сохранение сервера
- `ConfigurationManager.serializeNBT()` - сохранение всех данных

## Тестирование

Для тестирования в dev mode:
```bash
./gradlew runClient
```

Конфигурации будут сохранены в:
```
<game_directory>/config/bettertwink/configurations.nbt
```

## Производительность и оптимизация

- Сортировка вычисляется один раз в секунду максимум
- Поиск пути кэшируется
- Визуализация только на AFTER_TRANSLUCENT_BLOCKS стадии
- Все операции с инвентарем асинхронны

## Debugging

Включить логирование:
```
-Dlog4j.configurationFile=log4j2.xml
```

Просмотр конфигураций:
- Файл: `config/bettertwink/configurations.nbt` (NBT бинарный формат)
- Через GUI меню Better Twink (B клавиша)

## Совместимость

- Java 17+ required
- Forge 47.2.0+
- Minecraft 1.20.1
- Client-side only (no server modifications needed)

## Известные ограничения

1. Быстрое перемещение предметов может быть запрещено на некоторых серверах
   - Решение: увеличить задержку в конфигурации
2. Навигация работает только на земле/полу
   - TODO: добавить поддержку лестниц и других элементов
3. Крафт-система пока не полностью реализована
   - TODO: интеграция с таблицей рецептов Minecraft

## Будущие улучшения

- [ ] Полная система крафта
- [ ] Поддержка двойных сундуков
- [ ] AI навигация с препятствиями
- [ ] Web UI для управления конфигурацией
- [ ] Мультиплеер синхронизация (если возможна)
- [ ] Поддержка других типов контейнеров (сундуков, бочек и т.д.)
