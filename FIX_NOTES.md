# Что исправлено и доработано

## 1. Краш при открытии меню (главное)

Причина найдена точно, по байткоду библиотеки Material 1.13.0:

* `MinecraftActivity` объявлена в манифесте с темой `AppFullScreenTheme`
  (`Theme.AppCompat.NoActionBar`).
* Карточки модулей в меню (`item_mod_menu_card.xml` и
  `item_mod_menu_compact.xml`) были объявлены как
  `com.google.android.material.card.MaterialCardView`.
* `MaterialCardView` использует стиль `Widget.MaterialComponents.CardView`,
  у которого выставлен `enforceMaterialTheme=true`. В AppCompat-теме нет
  `isMaterialTheme`, поэтому конструктор бросает
  `IllegalArgumentException: The style on this component requires your app
  theme to be Theme.MaterialComponents (or a descendant)`.
* Это происходило уже после `windowManager.addView`, на layout-проходе
  RecyclerView, то есть **вне** `try/catch` метода `showInternal()` —
  поэтому приложение падало, а не открывало fallback.

Исправлено:

* `item_mod_menu_card.xml`, `item_mod_menu_compact.xml` переведены на
  `androidx.cardview.widget.CardView`; убраны Material-only атрибуты
  (`strokeColor`, `strokeWidth`, `rippleColor`), добавлен
  `android:foreground="?attr/selectableItemBackground"`. Внешний вид и
  обработка `setCardBackgroundColor()`/`setCardElevation()` сохранены.
* `ModMenuAdapter` дополнительно инфлейтит карточки через
  `ContextThemeWrapper(Theme.MaterialComponents.NoActionBar)`. Даже если
  позже в item-layout снова добавят Material-виджет, layout-проход не
  уронит игру.
* `ModMenuOverlay.showInternal()` ловит `Throwable`, логирует
  (`Log.e("ModMenuOverlay", ...)`), а `showFallback()` обёрнут в
  собственный try/catch. Ошибка открытия меню больше не приводит к
  падению процесса.
* В `ModMenuAdapter.onBindViewHolder()` добавлена защита от отсутствующих
  view в item-разметке.

## 2. TriggerBot

* Решение об ударе считается нативно по пересечению луча прицела
  (`ActorRotationComponent`) с мировым AABB актёра (slab method), а не по
  углу до цели aim assist. Работает при выключенном AimAssist.
* Список актёров теперь гарантированно содержит и мобов, и игроков:
  `Level::fetchNearbyActors` вызывается сначала с mob-маской, а если
  игроков в списке нет — дополнительно с полной маской; дубликаты
  отсекаются.
* Если прицел уже внутри хитбокса, aim assist обязан вести именно этого
  актёра (`triggerTarget` priority в `findTargetInternal`). Раньше
  доводка могла утащить камеру с хитбокса и удар отменялся.
* Допуск по хитбоксу уменьшен до 0.03 блока (компенсация интерполяции
  позиции между тиками), дальность — 3.0 блока до поверхности.
* `TriggerBotOverlay` не стреляет сквозь открытое меню клиента
  (`InbuiltOverlayManager.isModMenuShowing()`), паузу и HUD.
* Удар отправляется штатным путём игры: `performInbuiltAttack()` шлёт
  настоящий `MotionEvent` DOWN → MOVE → UP в `super.dispatchTouchEvent`.
  Урон, пакет атаки и анимация руки формируются самим клиентом.
  Координаты берутся из `decorView`, добавлен промежуточный MOVE, чтобы
  жест гарантированно склеивался gesture-детектором игры.

## 3. Обход серверных анти-читов (Polar / ML / Geyser)

Ключевой принцип: **никаких синтезированных пакетов**. Всё, что видит
сервер, генерирует сам ванильный клиент:

* поворот камеры идёт через штатный `applyTurnDelta` (тот же путь, что и
  ручной ввод), поэтому `PlayerAuthInput`/`MovePlayer` формируются игрой;
* `sendMovePacket()` остаётся no-op, если sender не зарегистрирован —
  лишних пакетов с «идеальными» углами нет.

Добавлен humanization-слой (`AimAssist`):

* реакция на новую цель 120–200 мс (база + случайный разброс) — камера не
  «прилипает» в кадр появления цели;
* микро-тремор камеры ~0.02° (две несоизмеримые синусоиды) — траектория
  перестаёт быть идеально прямой, это ломает эвристики rotation/aim
  pattern;
* ограничение поворота `maxTickDeltaDeg = 40°` за серверный тик (50 мс) —
  защита от rotation-снапов;
* сохранены ограничение скорости/ускорения, deadzone, sticky-target,
  рандомный CPS (620–790 мс), 6% пропусков и случайная задержка удара из
  `TriggerBotOverlay`.

Настройки доступны из JNI: `nativeSetHumanization`,
`nativeSetReactionDelay`, `nativeSetAimTremor` (обёртки в `AimAssistMod`).

Для Geyser это означает, что Bedrock-клиент не отправляет в прокси
аномальных углов/пакетов: Java-сервер видит обычный клиентский ввод.

## Сборка

`app/src/main/cpp/preloader` и `libHttpClient` в архиве — пустые каталоги
(submodules). Перед сборкой:

```
git submodule update --init --recursive
```

Далее Android SDK + NDK, `./gradlew assembleDebug` либо GitHub Actions
(тег `esp-debug`).

Проверка C++ (выполнена на clang 21, `-std=c++20 -fsyntax-only`):
`AimAssist.cpp`, `ClientBridge.cpp`, `Esp.cpp`, `aimassist.cpp` — без
ошибок.

## Что проверить на устройстве

* Версия игры 1.26.60.x (оффсеты/сигнатуры сняты с 1.26.60.4).
* `adb logcat -s AimAssist AimAssistBridge TriggerBot ModMenuOverlay`.
* Меню должно открываться без падения; если что-то пойдёт не так — в логе
  будет `ModMenuOverlay: showInternal failed ...` с причиной.
* `isOnTarget` становится true, когда прицел внутри хитбокса игрока/моба в
  пределах 3 блоков; удар сопровождается анимацией руки.
## 4. TriggerBot: почему он «вообще не бил» (найдено позже)

Две реальные причины, обе в Java-слое:

1. **Модуль не был зарегистрирован в оверлей-менеджере.**
   `InbuiltOverlayManager.showModOverlay()` не имел `case ModIds.TRIGGER_BOT`,
   `KEYBIND_MOD_IDS` не содержал `TRIGGER_BOT`, а
   `InbuiltModManager.getDefaultInbuiltModKeybind()` не задавал дефолтную
   клавишу. Итог: включение модуля в меню ничего не показывало, а shortcut
   отсутствовал. Добавлено: создание/показ/скрытие `TriggerBotOverlay`,
   регистрация в keybind-списке, дефолтная клавиша **G**, восстановление
   позиции и состояния.

2. **Инвертированная проверка HUD в цикле ударов.**
   `PreloaderInput.isHudScreenOpen()` == «идёт обычный геймплей» (так его
   используют `showGameOverlays`, `handleKeyEvent` и `pojavIsMenuOpen`).
   Старый guard останавливал триггер именно в геймплее:
   `if (isHudScreenOpen() ...) return;`. Исправлено на
   `if (!isHudScreenOpen() || isPauseMenuOpen() || isShowingMenu() || isModMenuShowing())`.

Дополнительно: расширены настройки (10 параметров, категории
`trigger_target` / `trigger_timing` / `appearance`):
задержка и разброс реакции, дальность, допуск хитбокса, бить игроков,
бить мобов, humanized-реакция, интервал и его разброс, шанс промаха,
клавиша активации. Все значения сохраняются в prefs и применяются нативно
через `AimAssistMod` (общие настройки триггера, Aim Assist не трогаем).

## 5. «Экран зажимается» и удар TriggerBot не доходит (корневая причина)

Обе проблемы — один и тот же баг в `MinecraftActivity` (Kotlin).

**Симптом.** Если на экране уже лежит палец (идёшь/держишь кнопку) и
срабатывает TriggerBot, он слал синтетический тап как `ACTION_DOWN` с
`pointerId = 0` — поверх живого жеста. Android/игра получали «второй
DOWN» без парного UP для уже существующего pointer'а: жест ломался, и
касание «залипало» (визуально — экран будто зажат, кнопка/камера ведут
себя как при постоянно нажатом пальце). Параллельно синтетический удар
при зажатом пальце часто не засчитывался, из-за чего TriggerBot
«вообще не бил».

**Исправление** (`MinecraftActivity.kt`):

* Добавлен трекинг реальных пальцев `trackRealTouch()` (вызывается первым
  в `dispatchTouchEvent`): карта `pointerId → координаты`, `downTime`
  живого жеста.
* `performInbuiltAttack()` больше не переиспользует `pointerId = 0`:
  * если пальцев нет — обычный `ACTION_DOWN`/`ACTION_UP`;
  * если палец уже на экране — тап идёт **дополнительным** pointer'ом
    (`ACTION_POINTER_DOWN`/`ACTION_POINTER_UP`) со свободным id (берём
    `MAX_POINTER_ID = 31` и понижаем до свободного), а `downTime` берётся
    от живого жеста — как требует Android при добавлении pointer'а.
* `createGameTouchEvent()` переписан под произвольное число pointer'ов
  (`action | (index << ACTION_POINTER_INDEX_SHIFT)`).
* Флаг `syntheticTapInFlight` не даёт наслаивать тапы: пока предыдущий
  между DOWN и UP — новый не начинается.
* UP/MOVE пересобирают список pointer'ов заново: если реальные пальцы
  поднялись за время удержания, синтетический остаётся один и корректно
  завершается обычным `ACTION_UP` (не оставляет «висящий» pointer).

## 6. TriggerBot: удар нажатием экранной кнопки атаки

Предыдущая попытка «нативного» удара (вызов `GameMode::attack` через
vtable игры) убрана из дерева полностью: она трогала внутренности
`libminecraftpe` и несла риск задеть остальные модули. Теперь удар идёт
через уже существующую кнопку атаки на экране.

**Как работает:**

* `PojavControls.pressAttackButton()` находит в текущем профиле контрол с
  `ControlData.SPECIALBTN_MOUSEPRI` — это штатная кнопка атаки (в дефолтном
  профиле она называется `PRI`), включая кнопки внутри drawer'ов.
* `PojavControlOverlay.RuntimeButton.tapForTriggerBot()` нажимает её
  `dispatchTouchEvent(ACTION_DOWN)` и через 60 мс отпускает
  `ACTION_UP` — те же события, что приходят от пальца, поэтому код
  обработки не отличается от ручного тапа.
* Игровая область экрана не получает ни одного касания: камера, ходьба и
  остальные контролы не задеты. Отпускание гарантировано (в т.ч. через
  `release()` при скрытии HUD/смене профиля), поэтому ничего не залипает.
* Если пальца на кнопке уже держится (`pressed == true`), повторный DOWN
  не отправляется — иначе сломался бы живой жест.
* `MinecraftActivity.performTriggerBotAttack()` — порядок выбора:
  1. кнопка атаки из Pojav Controls (`PojavControls.pressAttackButton()`);
  2. нативная инъекция кнопки мыши (`PojavControlsMod`), если кнопки в
     профиле нет;
  3. старый синтетический тап (`performInbuiltAttack()`), если Pojav
     Controls выключены.
* `TriggerBotOverlay` вызывает `performTriggerBotAttack()` вместо
  `performInbuiltAttack()`.

**Проверка:** включить TriggerBot, подойти к мобу/игроку так, чтобы прицел
попал в хитбокс, и убедиться, что удары идут, а камера не «зажата»
(`adb logcat -s TriggerBot AimAssist`).

## 7. TriggerBot: удар нативно через GameMode::attack (как в VHax)

Кнопочный вариант не сработал на устройстве, поэтому удар перенесён в
нативный слой — туда же, где живут ESP и aim assist, и туда же, где
атакуют внутренние читы (VHax `Aura`/`Killaura`): прямой вызов игрового
`GameMode::attack` на игровом потоке, без инъекции касаний.

**Проверено по `libminecraftpe.so` 1.26.60.04_RC3:**

* `ClientInstance + 0xA08` -> `GameMode*` (`SurvivalMode`); конструктор
  клиента делает `str x23,[x19,#0xA08]`, `GameMode` хранит обратную
  ссылку на владельца в `+0x8` (`stp x8,x1,[x0]`). Это и есть валидация
  объекта: `[gameMode+8] == clientInstance`.
* Слот 16 vtable `SurvivalMode` (`0x13d22900 + 16*8`) -> `0x100a11c8`:
  `mov x3,x2; mov w2,#1; b 0x1009cba4`, то есть
  `attack(Actor&, Vec3 const&)` с обязательным указателем на точку удара.
  `nullptr` в третьем аргументе роняет колбэк транзакции (он читает
  `Vec3` по этому указателю), поэтому передаётся точка пересечения луча
  прицела с хитбоксом.
* Внутри собирается `ItemUseOnActorInventoryTransaction` и уходит через
  `InventoryTransactionManager` — для сервера это обычная атака.

**Как работает:**

* `ClientBridge::getGameMode()` читает `[client+0xA08]`, проверяет
  обратную ссылку на клиент и адрес слота 16 через `dladdr`
  (`libminecraftpe.so`).
* `ClientBridge::attackActor(actor, hitPoint)` вызывает слот 16 — ту же
  виртуальную функцию, что и сам клиент.
* `AimAssist::updateTrigger()` после проверки луча сам выдерживает
  реакцию (`triggerDelayMs`) и интервал (`triggerIntervalMs`) и бьёт
  найденного актёра. Всё это на игровом потоке (`ClientInstance::update`).
* Java-цикл `TriggerBotOverlay` больше не инжектит касания: он только
  включает/выключает модуль и передаёт настройки в native.
* Новый JNI-метод: `AimAssistMod.setTriggerInterval(int)` /
  `nativeSetTriggerInterval`.

**Проверка:** включить TriggerBot, навести прицел на моба/игрока в
пределах reach и убедиться, что удары идут (`adb logcat -s AimAssist`).
