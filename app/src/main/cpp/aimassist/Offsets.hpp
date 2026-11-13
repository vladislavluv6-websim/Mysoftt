#pragma once

#include <cstdint>

// Все версионно-зависимые значения собраны здесь.
// При обновлении Minecraft меняются только сигнатуры/оффсеты в этом файле.
namespace aimbot::offsets {

inline constexpr const char *kMinecraftLibrary = "libminecraftpe.so";

// ============================================================
// СИГНАТУРЫ (ARM64, формат pl::memory::resolveSignature)
// ============================================================
inline constexpr const char *kNormalTickSignature =
    "? ? ? FC ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? 91 "
    "? ? ? D1 54 D0 3B D5 F3 03 00 AA ? ? ? F9 ? ? ? F8 ? ? ? 39";

inline constexpr const char *kClientInstanceUpdateSignature =
    "? ? ? FC ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? 91 "
    "? ? ? D1 59 D0 3B D5 ? ? ? 52 F5 03 02 2A";

inline constexpr const char *kClientInstanceGetLocalPlayerSignature =
    "? ? ? D1 ? ? ? A9 ? ? ? F9 ? ? ? 91 53 D0 3B D5 E8 03 00 AA ? ? ? 91 ? ? ? F9 "
    "? ? ? 91 ? ? ? F8 ? ? ? ? ? ? ? 91 ? ? ? ? ? ? ? 36 ? ? ? 91 ? ? ? 52 ? ? ? ? "
    "? ? ? ? E0 03 1F AA ? ? ? F9 ? ? ? F8 1F 01 09 EB ? ? ? 54 ? ? ? A9 ? ? ? F9 "
    "? ? ? 91 C0 03 5F D6 ? ? ? ? C0 03 5F D6";

inline constexpr const char *kApplyTurnDeltaSignature =
    "? ? ? D1 ? ? ? FD ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? 91 56 D0 3B D5 "
    "F3 03 00 AA F4 03 01 AA ? ? ? F9 ? ? ? F8 ? ? ? F9 ? ? ? F9";

inline constexpr const char *kFetchNearbyActorsSignature =
    "? ? ? D1 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? 91 "
    "5B D0 3B D5 F3 03 08 AA F5 03 02 2A";

// Touch-HUD: общий builder, который добавляет в вектор контролов пару
// attack (button.destroy_or_attack) + interact (button.build_or_interact).
// Через него проходят все схемы управления, поэтому хук ловит кнопку атаки
// в любой раскладке. Адрес 0x894a878 в 1.26.60.04_RC3.
inline constexpr const char *kTouchControlBuilderSignature =
    "FF 83 02 D1 FD 7B 05 A9 FA 67 06 A9 F8 5F 07 A9 F6 57 08 A9 F4 4F 09 A9 "
    "FD 43 01 91 56 D0 3B D5 69 00 80 52 F7 03 00 AA C8 16 40 F9 F3 03 00 AA "
    "A8 83 1F F8 08 F0 A7 52 E8 A7 02 29 E8 8E 42 F8 F5 82 5F F8 29 00 80 52 "
    "F4 42 00 D1 E9 73 00 39 BF 02 08 EB FF 43 00 39 E9 33 00 39 62 01 00 54";

// Смещения внутри структуры контрола (0xA0 байт), собранной в 0x895c488.
inline constexpr uintptr_t kControlActionString = 0x00;  // std::string
inline constexpr uintptr_t kControlRect = 0x24;          // 32-байтовый блок
inline constexpr uintptr_t kControlPosX = 0x54;          // float
inline constexpr uintptr_t kControlPosY = 0x58;          // float
inline constexpr uintptr_t kControlScale = 0x64;         // float
inline constexpr uintptr_t kControlIcon = 0x70;          // std::string
inline constexpr uintptr_t kControlIconPressed = 0x88;   // std::string
inline constexpr size_t kControlStride = 0xA0;
// Вектор контролов внутри объекта-layout'а: begin/end/cap.
inline constexpr uintptr_t kControlVectorBegin = 0x18;
inline constexpr uintptr_t kControlVectorEnd = 0x20;
inline constexpr uintptr_t kControlVectorCap = 0x28;
inline constexpr const char *kAttackActionName = "button.destroy_or_attack";

inline constexpr const char *kActorIsPlayerSignature =
    "? ? ? F9 ? ? ? 52 ? ? ? 72 ? ? ? A9 08 01 0B CB ? ? ? D3 ? ? ? 51 0C 01 0A 8A "
    "? ? ? B9 6B 0D 0C 8B ? ? ? F9 ? ? ? F9 ? ? ? B1 ? ? ? 54 8B 15 0B 8B ? ? ? B9 "
    "BF 01 0A 6B ? ? ? 54 ? ? ? F9 7F 01 09 EB ? ? ? 54 ? ? ? F9 ? ? ? B4 ? ? ? A9 "
    "? ? ? D3 6B 01 09 CB 5F 0D 8B EB ? ? ? 54 ? ? ? F8 ? ? ? B4 ? ? ? 92 ? ? ? 12 "
    "? ? ? 92 ? ? ? B8 28 01 08 4A ? ? ? 12 1F 01 09 6B E0 27 9F 1A C0 03 5F D6 "
    "E0 03 1F 2A C0 03 5F D6 ? ? ? D1 ? ? ? FD";

// GetFov: float(void *self, float delta, int variable) — живой вертикальный
// FOV в градусах (1..179) при variable != 0 (проверено motionblur BedrockTools).
inline constexpr const char *kGetFovSignature =
    "? ? ? FC ? ? ? 6D ? ? ? A9 ? ? ? F9 ? ? ? A9 ? ? ? 91 08 40 20 1E "
    "F4 03 01 2A";

// Fullbright: функция, возвращающая уровень освещения (float). Патч
// возвращает 1000.0f, поэтому затемнение/туман пропадают.
inline constexpr const char *kFullbrightSignature =
    "? ? ? A9 FD 03 00 91 ? ? ? F9 ? ? ? 52 ? ? ? F9 00 01 3F D6 ? ? ? A8 "
    "? ? ? ? ? ? ? A9 ? ? ? F9 FD 03 00 91 ? ? ? F9 F3 03 01 2A ? ? ? 52 "
    "? ? ? F9 00 01 3F D6 E1 03 13 2A ? ? ? F9 ? ? ? A8 ? ? ? ? ? ? ? A9 "
    "? ? ? A9";

inline constexpr const char *kFullbrightPatchHex =
    "40 8F A8 52 00 00 27 1E C0 03 5F D6";

// Actor::getNameTag() — ARM64-сигнатура для 1.26.6X (любезно проверена
// BedrockTools, тот же Preloader/ABI). Возвращает std::string по значению.
inline constexpr const char *kActorGetNameTagSignature =
    "? ? ? D1 ? ? ? A9 ? ? ? F9 ? ? ? A9 ? ? ? 91 55 D0 3B D5 F3 03 08 AA "
    "F4 03 00 AA ? ? ? F9 ? ? ? F8 E8 03 00 91 ? ? ? ? E0 03 00 91";

// Поля имени (Android 1.26.6X, ARM64):
//   Actor::mFilteredNameTag — std::string с уже отфильтрованным ником;
//   Player::mName           — std::string с чистым ником игрока.
inline constexpr uintptr_t kActorFilteredNameTag = 0x2E0;
inline constexpr uintptr_t kPlayerName = 0xB28;

// ============================================================
// ESP / hitbox render (1.26.60, ARM64)
// ============================================================
inline constexpr const char *kRenderLevelSignature =
    "? ? ? D1 ? ? ? FD ? ? ? 6D ? ? ? 6D ? ? ? 6D ? ? ? A9 ? ? ? A9 ? ? ? A9 "
    "? ? ? A9 ? ? ? A9 ? ? ? 91 57 D0 3B D5";

inline constexpr const char *kTessellatorBeginSignature =
    "? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 FD 03 00 91 ? ? ? 39 ? ? ? 39 08 01 09 2A";

inline constexpr const char *kTessellatorColorSignature =
    "? ? ? 52 ? ? ? 39 04 01 27 1E 00 08 24 1E 21 08 24 1E 42 08 24 1E 63 08 24 1E 08 00 38 1E";

inline constexpr const char *kTessellatorVertexSignature =
    "? ? ? D1 ? ? ? FD ? ? ? 6D ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 "
    "? ? ? A9 ? ? ? 91 5B D0 3B D5 ? ? ? F9 ? ? ? F9";

inline constexpr const char *kRenderMeshImmediatelySignature =
    "? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 FD 03 00 91 ? ? ? D1 57 D0 3B D5 F6 03 00 AA "
    "E0 03 01 AA ? ? ? F9 F4 03 03 AA";

// Функция, в начале которой ADRP+ADD грузит адрес общего RenderMaterialGroup.
inline constexpr const char *kRenderMaterialGroupCommonSignature =
    "? ? ? ? ? ? ? 91 ? ? ? D1 ? ? ? D1 ? ? ? 91 E3 03 14 AA ? ? ? 52 "
    "? ? ? ? ? ? ? F9 ? ? ? A9 ? ? ? B4 ? ? ? 91 ? ? ? 92 ? ? ? ? "
    "? ? ? B5 ? ? ? F9 E0 03 14 AA ? ? ? F9 00 01 3F D6 E0 03 14 AA ? ? ? ? "
    "? ? ? F9 ? ? ? F9 ? ? ? B4 ? ? ? 91 ? ? ? 92 ? ? ? ? ? ? ? B5 "
    "? ? ? F9 E0 03 14 AA ? ? ? F9 00 01 3F D6 E0 03 14 AA ? ? ? ? ? ? ? 38 "
    "? ? ? 37 ? ? ? 39 ? ? ? 37 ? ? ? F8 ? ? ? A9 ? ? ? B4 ? ? ? 91 "
    "? ? ? 92 ? ? ? ? ? ? ? B5 ? ? ? F9 E0 03 14 AA ? ? ? F9 00 01 3F D6 "
    "E0 03 14 AA ? ? ? ? ? ? ? F8 ? ? ? F8 ? ? ? B4 ? ? ? 91 ? ? ? 92 "
    "? ? ? ? ? ? ? B5 ? ? ? F9 E0 03 14 AA ? ? ? F9 00 01 3F D6 E0 03 14 AA "
    "? ? ? ? ? ? ? ? ? ? ? F9 ? ? ? 38 ? ? ? 36 ? ? ? F8 ? ? ? ? "
    "? ? ? 39 ? ? ? 36 ? ? ? F9 ? ? ? ? ? ? ? F8 ? ? ? A9 ? ? ? B5 "
    "? ? ? F8 ? ? ? F9 ? ? ? A9 ? ? ? F9 ? ? ? F9 ? ? ? B4 ? ? ? A9 "
    "? ? ? B4 ? ? ? 91 ? ? ? 52 ? ? ? ? ? ? ? F9 ? ? ? F9 ? ? ? B4 "
    "? ? ? F9 ? ? ? 91 E1 03 13 AA ? ? ? 91 ? ? ? ? ? ? ? 39 ? ? ? A9 "
    "? ? ? 72 ? ? ? D3 29 15 94 9A 08 01 8A 9A ? ? ? A9 ? ? ? D1 ? ? ? 91 "
    "? ? ? ? ? ? ? F9 ? ? ? F9 ? ? ? F9 E8 03 00 91";
inline constexpr uintptr_t kRenderMaterialGroupOffset = 32;

// ============================================================
// OFFSETS (Minecraft Bedrock 1.26.60, ARM64)
// ============================================================
// Actor -> StateVectorComponent*, внутри StateVectorComponent + 0 -> Vec3.
inline constexpr uintptr_t kActorStateVectorComponent = 0x228;
inline constexpr uintptr_t kActorAabbComponent = 0x230;
inline constexpr uintptr_t kStatePosition = 0;

// Actor::mCategories (uint32).
inline constexpr uintptr_t kActorCategories = 0x220;

// ActorRotationComponent: { pitch, yaw }.
// applyTurnDelta двигает именно этот компонент; читаем его каждый кадр.
inline constexpr uintptr_t kActorRotationComponent = 0x238;

// Состояние взмаха руки у LocalPlayer (подтверждено кодом 0xfa85d20/0xfa887f0):
//   +0x404 float  — прогресс взмаха 0..1 (движок считает из счётчика);
//   +0x408 int32  — счётчик тиков взмаха (-1 сразу после старта, затем 0..);
//   +0x434 uint16 — исходник взмаха (младший байт) + флаг mSwinging (старший
//                   байт: 0x100 => взмах идёт);
//   +0x450 float  — предыдущий прогресс для интерполяции.
// Старт взмаха делает функция 0xfa887f0(this, w1, source): пишет
// counter=-1 и половину 0x434 = 0x100 | source. Её и вызываем вместо
// ручной записи полей (0x1009cba4 сам взмах не запускает).
inline constexpr uintptr_t kPlayerSwingTime = 0x404;
inline constexpr uintptr_t kPlayerSwingTicks = 0x408;
inline constexpr uintptr_t kPlayerSwingState = 0x434;
inline constexpr uintptr_t kPlayerSwingTimePrev = 0x450;
inline constexpr uintptr_t kPlayerSwingFnFileOffset = 0xfa887f0;

// Если известны flags / team — заполни. 0 = фича отключена.
inline constexpr uintptr_t kActorFlags = 0;
inline constexpr uintptr_t kActorTeam = 0;

inline constexpr uint64_t kFlagInvisible = 1ULL << 0;

// ActorType, который возвращает fetchNearbyActors для мобов/игроков.
inline constexpr int kActorTypeMob = 0x100;
// Полный набор категорий: некоторые сборки под моб-маской не отдают
// игроков, поэтому триггер дополнительно запрашивает всё и фильтрует сам.
inline constexpr int kActorTypeAll = -1;

// ============================================================
// КОНСТАНТЫ ЦЕЛИ
// ============================================================
// Рост глаз игрока над низом хитбокса.
inline constexpr float kEyeHeight = 1.62f;
// Точка прицеливания как доля высоты хитбокса (от низа).
inline constexpr float kHeadFraction = 0.90f;
// Верх груди / шея: между грудью (0.62) и головой (0.88).
inline constexpr float kUpperChestFraction = 0.78f;
inline constexpr float kChestFraction = 0.65f;
// Чуть ниже груди (нижняя часть грудной клетки / солнечное сплетение).
inline constexpr float kLowerChestFraction = 0.55f;
inline constexpr float kLegsFraction = 0.25f;

// ============================================================
// GAMEMODE (атака)
// ============================================================
// ClientInstance -> GameMode* (SurvivalMode). Проверено по 1.26.60.04_RC3:
// ClientInstance::ctor делает str x23,[x19,#0xA08].
inline constexpr uintptr_t kClientInstanceGameMode = 0xA08;
// GameMode+0x8 — указатель на Level/ClientInstance (объект с vtable в игре).
// Раньше считалось, что это ровно ClientInstance; на практике +0x8 ведёт на
// Level, поэтому проверку "owner == clientAddr" использовать нельзя.
inline constexpr uintptr_t kGameModeOwner = 0x8;
// GameMode+0xC0 — объект с vtable в игре (используется внутри attack-реализации).
inline constexpr uintptr_t kGameModeAux = 0xC0;
// GameMode+0xC8 — bool-флаг (0 — клиент/разрешить, 1 — сервер/проверить global).
inline constexpr uintptr_t kGameModeBool = 0xC8;
// Слот 16 vtable GameMode (историческая догадка; на деле attack не в vtable).
inline constexpr size_t kGameModeAttackSlot = 16;
// Файловый оффсет публичной обёртки GameMode::attack в 1.26.60.4: читает
// [this+0xC8], проверяет global 0x141d7040, затем w2=1 и tail-call в 4-арг
// реализацию 0x1009cba4(this, actor, actionType=1, Vec3 const&).
inline constexpr uintptr_t kSurvivalModeAttackFileOffset = 0x100a11c8;
// 4-арг реализация атаки. Именно её напрямую зовёт игровой обработчик
// 0xce2c894: 0x1009cba4(GameMode, actor, actionType=0, Vec3 const&). Повторяем
// этот вызов — без флаговой проверки обёртки.
inline constexpr uintptr_t kGameModeAttackImplFileOffset = 0x1009cba4;
// Обработчик списка атак (0xce2c78c). Каждый тик получает WeakPtr на
// настоящий ClientInstance и зовёт getGameMode(lock(a2)). Хук ловит этот
// ClientInstance и кэширует реальный GameMode.
inline constexpr uintptr_t kAttackListProcessorFileOffset = 0xce2c78c;
// lock(WeakPtr, 0): разыменовывает WeakPtr и проверяет [+0x269] == 0.
inline constexpr uintptr_t kLockWeakPtrFileOffset = 0xfdb3b20;
// ClientInstance vtable (vaddr). [client] == base + 0x1388FE70.
inline constexpr uintptr_t kClientInstanceVtableFileOffset = 0x1388FE70;

// ============================================================
// ESP RENDER OFFSETS
// ============================================================
inline constexpr uintptr_t kScreenContextTessellator = 0xB0;
inline constexpr uintptr_t kScreenContextColorHolder = 0x30;
inline constexpr uintptr_t kShaderColorDirty = 0x10;
inline constexpr uintptr_t kLevelRendererPlayer = 0x430;
inline constexpr uintptr_t kLevelRendererPlayerCamPos = 0x614;
// FOV (радианы или градусы — определяется по величине). Два кандидата:
// LevelRendererPlayer (проверено в 1.21.80) и ClientInstance.
inline constexpr uintptr_t kLevelRendererPlayerFovX = 0xF58;
inline constexpr uintptr_t kLevelRendererPlayerFovY = 0xF6C;
inline constexpr uintptr_t kClientInstanceFovX = 0x740;
inline constexpr uintptr_t kClientInstanceFovY = 0x754;
inline constexpr uintptr_t kLevelRendererSelectionMaterial = 0x10A0;
inline constexpr uint32_t kMobCategory = 2;


// 1.26.60.04: PlayerAuthInputPacket::write (packet id 0x90). Used only for
// stripping sprint bits during the TriggerBot critical window.
inline constexpr uintptr_t kPlayerAuthInputWriteFileOffset = 0xdaa4864;
inline constexpr uintptr_t kPlayerAuthInputInputData = 0x88;
inline constexpr uintptr_t kPlayerAuthInputRot = 0x30;
inline constexpr uintptr_t kPlayerAuthInputYHeadRot = 0x44;

} // namespace aimbot::offsets
