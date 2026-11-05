# Context: Create Optical Diffuser

Повна документація архітектури, принципів роботи та історії помилок: [DEVELOPER_GUIDE.md](file:///home/mik/.local/share/atlauncher/instances/AllofAeronauticsNEW/create_optical_diffuser/DEVELOPER_GUIDE.md).

## 1. Quick Summary (Швидкий огляд)
- **Mod**: Create: Optical Diffuser (NeoForge 1.21.1)
- **Платформа**: Minecraft 1.21.1, NeoForge 21.1.x, Create 6.0.10, Create Optical 0.4.2
- **Основні блоки**:
  1. `OpticalDiffuserBlock` (`create_optical_diffuser:optical_diffuser`) — прожектор/маяк, що розгортає тонкий лазер у волюметричний конус світла.
  2. `LeadGlassBlock` (`create_optical_diffuser:lead_glass`) — свинцеве скло (пропускає видиме світло, але на 100% захищає від гамма-радіації; підтримує CTM/Fusion з'єднання).
- **Збірка мода**:
  ```bash
  cd create_optical_diffuser
  ./gradlew build -x test
  cp build/libs/create_optical_diffuser-1.0.0.jar ../mods/
  ```

---

## 2. Поточний стан механік (Актуальна версія)
1. **Повна відсутність GUI (In-World взаємодія):**
   - Усі налаштування відбуваються в світі без меню.
   - Гайковий ключ Create (**Wrench**): звичайний клік повертає дифузор, **Shift + ПКМ** миттєво демонтує його в інвентар гравця.
2. **Модулі лінз (Focuser та Condenser):**
   - **Вставка:** Клік ПКМ предметом `create_optical:beam_focuser` або `create_optical:beam_condenser` (або фізичне встановлення блоку впритул з будь-якого боку).
   - **Вилучення:** **Shift + ПКМ порожньою рукою** (модуль випадає).
   - Автоматично випадає при руйнуванні або демонтажі блоку.
3. **Логіка редстоуну та форми променя:**
   - **Рівень 15:** повне вимкнення променя та освітлення (для всіх режимів).
   - **Без модуля (дефолт):** сигнал 0..14 тримає базовий кут $35^\circ$ (вимикається тільки на 15).
   - **З `BEAM_FOCUSER`:** сигнал 0..14 **фокусує** промінь від $45^\circ$ (при 0) до тонких $8^\circ$ (при 14), дальність зростає до 96 блоків.
   - **З `BEAM_CONDENSER`:** сигнал 0..14 **розсіює** промінь від $20^\circ$ (при 0) до широких $70^\circ$ (при 14).
4. **Суворий задній порт лазера:**
   - `canReceive(...)` приймає лазер тільки з протилежного боку від лінзи (`properties.direction() == state.getValue(FACING)`). Лазери з боків чи в переднє скло блокуються.
5. **Кінетичне обертання:**
   - Підключення вала Create знизу обертає корпус і конус світла на 360° зі швидкістю вала (RPM).
6. **Окуляри інженера (Engineer's Goggles):**
   - Показують статус, тип променя, дальність, кут, встановлений модуль лінзи та рівень редстоуну ($X/15$).
7. **Безшовне з'єднання (CTM) для Lead Glass:**
   - Реалізовано через мод **Fusion** (розкладка `pieced`, 80×16 текстура на 5 тайлів у `fusion-overrides/`).
   - У `neoforge.mods.toml` прописано `fusion` як optional client dependency.
8. **Лут і видобуток:**
   - Обидва блоки зареєстровані в `#minecraft:mineable/pickaxe` та мають повноцінні `loot_table`. Швидко добуваються кайлом і гарантовано випадають.
9. **Волюметричний рендеринг:**
   - 12 концентричних оболонок (`SHELL_RADII` від 0.04 до 1.00) з адитивним блендингом `(SRC_ALPHA, ONE)`.
   - Плавний атмосферний спад, пом'якшення силуетних країв, розсіювання Мі при погляді на маяк та Гаусове розжарене ядро.
   - Динамічні тіні (raycast occlusion) видалені, щоб запобігти геометричним спотворенням.

---

## 3. Файлова структура
```
create_optical_diffuser/
├── src/main/java/com/mik/optical_diffuser/
│   ├── OpticalDiffuserMod.java           # Головний клас ініціалізації
│   ├── ModRegistry.java                  # Реєстрація блоків, предметів, BE
│   ├── block/
│   │   ├── OpticalDiffuserBlock.java     # Логіка блоку, Wrench, модулі, задній порт
│   │   └── LeadGlassBlock.java           # Свинцеве скло з culling внутрішніх граней
│   ├── block/entity/
│   │   └── OpticalDiffuserBlockEntity.java # Стан, редстоун, кут, субтрактивний колір, світло
│   ├── client/render/
│   │   └── OpticalDiffuserRenderer.java  # 12-оболонковий волюметричний шейдер
│   └── compat/
│       └── SableCompat.java              # Сумісність із дирижаблями та кораблями Sable
└── src/main/resources/
    ├── pack.mcmeta                       # Оверлей для Fusion
    ├── fusion-overrides/                 # Безшовні моделі і текстури Fusion для Lead Glass
    ├── data/create_optical_diffuser/loot_table/blocks/ # Луттейбли (випадання блоків)
    └── data/minecraft/tags/block/mineable/pickaxe.json # Тег кайла
```
