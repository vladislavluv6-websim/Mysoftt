#include "Esp.hpp"

#include <android/log.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <string>

#include "AntiBot.hpp"
#include "GameEcs.hpp"
#include "Memory.hpp"
#include "Offsets.hpp"
#include "pl/memory/Hook.hpp"
#include "pl/memory/Signature.hpp"

namespace aimbot {
namespace {

constexpr const char *kTag = "AimAssistEsp";

// mce::PrimitiveMode
constexpr int kPrimitiveQuadList = 1;
constexpr int kPrimitiveLineList = 4;

constexpr uint32_t kFriendColor = 0xFF55FF66u;

// Прозрачность заливки относительно альфы выбранного цвета.
constexpr float kFillAlpha = 0.38f;
// Чуть раздуваем бокс, чтобы заливка не мерцала на модели.
constexpr float kInflate = 0.02f;

using TessellatorBeginFn = void (*)(void *, void *, int, int, int);
using TessellatorColorFn = void (*)(void *, float, float, float, float);
using TessellatorVertexFn = void (*)(void *, float, float, float);
using RenderMeshFn = void (*)(void *, void *, void *, char *);
using RenderLevelFn = void (*)(void *, void *, void *);

TessellatorBeginFn g_tessBegin = nullptr;
TessellatorColorFn g_tessColor = nullptr;
TessellatorVertexFn g_tessVertex = nullptr;
RenderMeshFn g_renderMesh = nullptr;
RenderLevelFn g_renderLevelOriginal = nullptr;

// ------------------------------------------------------------
// Материалы
// ------------------------------------------------------------
// Раскладка как в игре (libc++): hash, std::string, lastMatch.
struct HashedString {
  uint64_t hash = 0;
  std::string str;
  const HashedString *lastMatch = nullptr;

  explicit HashedString(const char *value) : str(value ? value : "") {
    if (str.empty()) return;
    constexpr uint64_t kOffset = 0xCBF29CE484222325ULL;
    constexpr uint64_t kPrime = 0x100000001B3ULL;
    uint64_t h = kOffset;
    for (char ch : str) {
      h = static_cast<uint64_t>(static_cast<unsigned char>(ch)) ^ (kPrime * h);
    }
    hash = h;
  }
};

// mce::MaterialPtr = shared_ptr. Нетривиальный деструктор нужен, чтобы
// ABI возвращал его через x8, как в игре. Ссылку намеренно не отпускаем:
// материал живёт всё время работы игры.
struct MaterialPtr {
  void *data[2]{nullptr, nullptr};

  MaterialPtr() = default;
  MaterialPtr(const MaterialPtr &) = delete;
  MaterialPtr &operator=(const MaterialPtr &) = delete;
  MaterialPtr(MaterialPtr &&other) noexcept {
    data[0] = other.data[0];
    data[1] = other.data[1];
    other.data[0] = nullptr;
    other.data[1] = nullptr;
  }
  MaterialPtr &operator=(MaterialPtr &&other) noexcept {
    if (this != &other) {
      data[0] = other.data[0];
      data[1] = other.data[1];
      other.data[0] = nullptr;
      other.data[1] = nullptr;
    }
    return *this;
  }
  ~MaterialPtr() {}

  explicit operator bool() const { return data[0] != nullptr; }
};

using GetMaterialFn = MaterialPtr (*)(void *, const HashedString *);

uintptr_t g_materialGroup = 0;
MaterialPtr g_xrayMaterial;
const char *g_xrayMaterialName = nullptr;
int g_materialAttempts = 0;
int g_materialRetryFrames = 0;

// Материалы без depth-test идут первыми: так бокс виден сквозь стены.
constexpr const char *kXrayMaterials[] = {
    "name_tag",
    "ui_fill_color",
    "selection_overlay",
    "selection_box",
};

// insns — локальная копия инструкций, pc — их реальный адрес в .text.
uintptr_t resolveAdrp(const uint32_t *insns, uintptr_t pc, size_t count,
                      uint32_t targetReg) {
  for (size_t i = 0; i < count; i++) {
    const uint32_t insn = insns[i];
    if ((insn & 0x1F) != targetReg) continue;

    if ((insn & 0x9F000000) == 0x90000000) {
      const int64_t immhi = (insn >> 5) & 0x7FFFF;
      const int64_t immlo = (insn >> 29) & 0x3;
      int64_t imm = (immhi << 2) | immlo;
      if (imm & (1LL << 20)) imm -= (1LL << 21);
      const uintptr_t page = ((pc + i * 4) & ~0xFFFULL) +
                             static_cast<uintptr_t>(imm * 4096);

      for (size_t j = i + 1; j < count; j++) {
        const uint32_t add = insns[j];
        if ((add & 0xFF000000) == 0x91000000 &&
            ((add >> 5) & 0x1F) == targetReg && (add & 0x1F) == targetReg) {
          uint32_t imm12 = (add >> 10) & 0xFFF;
          if (add & 0x400000) imm12 <<= 12;
          return page + imm12;
        }
        if ((add & 0x1F) == targetReg) break;
      }
    }
  }
  return 0;
}

void resolveMaterialGroup() {
  const uintptr_t fn = pl::memory::resolveSignature(
      offsets::kRenderMaterialGroupCommonSignature, offsets::kMinecraftLibrary);
  if (!fn || !mem::isGameAddress(reinterpret_cast<const void *>(fn))) {
    __android_log_print(ANDROID_LOG_WARN, kTag,
                        "RenderMaterialGroup signature not found");
    return;
  }
  uint32_t insns[8]{};
  if (!mem::read(fn, insns, sizeof(insns))) return;
  const uintptr_t group = resolveAdrp(insns, fn, 8, 0);
  if (!group) {
    __android_log_print(ANDROID_LOG_WARN, kTag, "RenderMaterialGroup ADRP failed");
    return;
  }
  g_materialGroup = group + offsets::kRenderMaterialGroupOffset;
  __android_log_print(ANDROID_LOG_INFO, kTag, "RenderMaterialGroup=%p",
                      reinterpret_cast<void *>(g_materialGroup));
}

bool lookupMaterial(const char *name, MaterialPtr &out) {
  if (!g_materialGroup) return false;
  uintptr_t vtable = 0;
  if (!mem::read(g_materialGroup, vtable) || !mem::isPlausiblePointer(vtable)) {
    return false;
  }
  uintptr_t getMaterial = 0;
  if (!mem::read(vtable + 2 * sizeof(void *), getMaterial) ||
      !mem::isGameAddress(reinterpret_cast<const void *>(getMaterial))) {
    return false;
  }
  HashedString key(name);
  out = reinterpret_cast<GetMaterialFn>(getMaterial)(
      reinterpret_cast<void *>(g_materialGroup), &key);
  return static_cast<bool>(out);
}

void ensureXrayMaterial() {
  if (g_xrayMaterial || !g_materialGroup) return;
  if (g_materialRetryFrames > 0) {
    --g_materialRetryFrames;
    return;
  }
  if (g_materialAttempts >= 30) return;
  ++g_materialAttempts;
  g_materialRetryFrames = 120;

  for (const char *name : kXrayMaterials) {
    MaterialPtr material;
    if (lookupMaterial(name, material)) {
      g_xrayMaterial = std::move(material);
      g_xrayMaterialName = name;
      __android_log_print(ANDROID_LOG_INFO, kTag, "ESP material: %s", name);
      return;
    }
  }
}

// ------------------------------------------------------------
// Цвет шейдера
// ------------------------------------------------------------
struct ColorState {
  uintptr_t holder = 0;
  std::byte saved[16]{};

  explicit ColorState(void *screenContext) {
    uintptr_t context = reinterpret_cast<uintptr_t>(screenContext);
    if (!mem::isPlausiblePointer(context)) return;
    if (!mem::read(context + offsets::kScreenContextColorHolder, holder) ||
        !mem::isPlausiblePointer(holder)) {
      holder = 0;
      return;
    }
    if (!mem::read(holder, saved, sizeof(saved))) {
      holder = 0;
      return;
    }
    const float white[4] = {1.0f, 1.0f, 1.0f, 1.0f};
    if (!mem::write(holder, white, sizeof(white))) {
      holder = 0;
      return;
    }
    markDirty();
  }

  ~ColorState() {
    if (!holder) return;
    if (mem::write(holder, saved, sizeof(saved))) markDirty();
  }

  explicit operator bool() const { return holder != 0; }

  void markDirty() const {
    const uint8_t dirty = 1;
    mem::write(holder + offsets::kShaderColorDirty, dirty);
  }
};

void renderLevelDetour(void *levelRenderer, void *screenContext, void *a3) {
  if (g_renderLevelOriginal) {
    g_renderLevelOriginal(levelRenderer, screenContext, a3);
  }
  Esp::instance().render(levelRenderer, screenContext);
}

bool validGameFn(uintptr_t address) {
  return address != 0 &&
         mem::isGameAddress(reinterpret_cast<const void *>(address));
}

struct Rgba {
  float r, g, b, a;
};

Rgba toRgba(uint32_t argb) {
  return {static_cast<float>((argb >> 16) & 0xFF) / 255.0f,
          static_cast<float>((argb >> 8) & 0xFF) / 255.0f,
          static_cast<float>(argb & 0xFF) / 255.0f,
          static_cast<float>((argb >> 24) & 0xFF) / 255.0f};
}

void submit(void *tessellator, void *screenContext, void *material) {
  char pad[0x58];
  std::memset(pad, 0, sizeof(pad));
  g_renderMesh(screenContext, tessellator, material, pad);
}

// Заливка: 6 граней, каждая в обе стороны (без зависимости от culling).
void drawFilledBox(void *tessellator, void *screenContext, void *material,
                   const Vec3 c[8], const Rgba &color) {
  static constexpr int kFaces[6][4] = {
      {0, 1, 2, 3}, // низ
      {4, 7, 6, 5}, // верх
      {0, 4, 5, 1}, // север
      {2, 6, 7, 3}, // юг
      {3, 7, 4, 0}, // запад
      {1, 5, 6, 2}, // восток
  };

  g_tessBegin(tessellator, nullptr, kPrimitiveQuadList, 48, 0);
  g_tessColor(tessellator, color.r, color.g, color.b, color.a * kFillAlpha);
  for (const auto &face : kFaces) {
    for (int i = 0; i < 4; ++i) {
      const Vec3 &p = c[face[i]];
      g_tessVertex(tessellator, p.x, p.y, p.z);
    }
    for (int i = 3; i >= 0; --i) {
      const Vec3 &p = c[face[i]];
      g_tessVertex(tessellator, p.x, p.y, p.z);
    }
  }
  submit(tessellator, screenContext, material);
}

void drawOutline(void *tessellator, void *screenContext, void *material,
                 const Vec3 c[8], const Rgba &color) {
  static constexpr int kEdges[12][2] = {
      {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6},
      {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7},
  };
  g_tessBegin(tessellator, nullptr, kPrimitiveLineList, 24, 0);
  g_tessColor(tessellator, color.r, color.g, color.b, color.a);
  for (const auto &edge : kEdges) {
    const Vec3 &p1 = c[edge[0]];
    const Vec3 &p2 = c[edge[1]];
    g_tessVertex(tessellator, p1.x, p1.y, p1.z);
    g_tessVertex(tessellator, p2.x, p2.y, p2.z);
  }
  submit(tessellator, screenContext, material);
}

void drawBox(void *tessellator, void *screenContext, void *material,
             const Aabb &aabb, const float camera[3], uint32_t argb) {
  if (!tessellator || !screenContext || !material) return;

  const float x0 = aabb.min.x - kInflate - camera[0];
  const float y0 = aabb.min.y - kInflate - camera[1];
  const float z0 = aabb.min.z - kInflate - camera[2];
  const float x1 = aabb.max.x + kInflate - camera[0];
  const float y1 = aabb.max.y + kInflate - camera[1];
  const float z1 = aabb.max.z + kInflate - camera[2];
  const Vec3 corners[8] = {
      {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1},
      {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1},
  };

  const Rgba color = toRgba(argb);
  drawFilledBox(tessellator, screenContext, material, corners, color);
  drawOutline(tessellator, screenContext, material, corners, color);
}

} // namespace


// ------------------------------------------------------------
// Name Tags
// ------------------------------------------------------------
using ActorGetNameTagFn = std::string (*)(void *);

ActorGetNameTagFn g_actorGetNameTag = nullptr;
ActorGetNameTagFn g_actorGetNameTagOriginal = nullptr;
std::once_flag g_actorGetNameTagOnce;

// Пока Name Tags включён, ванильный рендер получает пустую строку и не
// рисует серверные ники. Свой слой берёт имя из Player::mName и кэша,
// поэтому хук не влияет на наши подписи.
std::string actorGetNameTagDetour(void *actor) {
  if (Esp::instance().isNameTagsEnabled()) return {};
  return g_actorGetNameTagOriginal ? g_actorGetNameTagOriginal(actor)
                                   : std::string{};
}

using GetFovFn = float (*)(void *, float, int);
GetFovFn g_getFovOriginal = nullptr;
std::atomic<float> g_worldFovDeg{0.0f};

float getFovDetour(void *self, float delta, int variable) {
  const float value =
      g_getFovOriginal ? g_getFovOriginal(self, delta, variable) : 70.0f;
  if (variable && std::isfinite(value) && value > 1.0f && value < 179.0f) {
    g_worldFovDeg.store(value, std::memory_order_relaxed);
  }
  return value;
}

ActorGetNameTagFn actorGetNameTagFn() {
  std::call_once(g_actorGetNameTagOnce, [] {
    const uintptr_t fn = pl::memory::resolveSignature(
        offsets::kActorGetNameTagSignature, offsets::kMinecraftLibrary);
    if (!fn || !mem::isGameAddress(reinterpret_cast<const void *>(fn))) return;
    auto target = reinterpret_cast<ActorGetNameTagFn>(fn);
    pl::memory::FuncPtr original = nullptr;
    if (pl::memory::hook(reinterpret_cast<pl::memory::FuncPtr>(target),
                         reinterpret_cast<pl::memory::FuncPtr>(
                             &actorGetNameTagDetour),
                         &original) == 0 &&
        original) {
      g_actorGetNameTagOriginal =
          reinterpret_cast<ActorGetNameTagFn>(original);
      g_actorGetNameTag = g_actorGetNameTagOriginal;
    } else {
      // Хук не встал: читаем имя напрямую, но ванильные ники скрыть
      // не получится.
      g_actorGetNameTagOriginal = target;
      g_actorGetNameTag = target;
    }
  });
  return g_actorGetNameTag;
}

// Убираем §-коды, управляющие символы и лишние пробелы.
std::string cleanPlayerName(std::string name) {
  std::string out;
  out.reserve(name.size());
  for (size_t i = 0; i < name.size();) {
    const unsigned char c = static_cast<unsigned char>(name[i]);
    // § в UTF-8 (U+00A7) + один символ форматирования.
    if (c == 0xC2 && i + 1 < name.size() &&
        static_cast<unsigned char>(name[i + 1]) == 0xA7) {
      i += 2;
      if (i < name.size()) {
        const unsigned char d = static_cast<unsigned char>(name[i]);
        if ((d & 0x80) == 0) i += 1;
        else if ((d & 0xE0) == 0xC0) i += 2;
        else if ((d & 0xF0) == 0xE0) i += 3;
        else if ((d & 0xF8) == 0xF0) i += 4;
        else i += 1;
      }
      continue;
    }
    if (c == 0xA7) {
      i += 1;
      if (i < name.size() &&
          (static_cast<unsigned char>(name[i]) & 0x80) == 0) {
        i += 1;
      }
      continue;
    }
    if (c < 0x20) {
      ++i;
      continue;
    }
    out.push_back(static_cast<char>(c));
    ++i;
  }
  while (!out.empty() && (out.back() == ' ' || out.back() == '\t')) {
    out.pop_back();
  }
  size_t start = 0;
  while (start < out.size() && (out[start] == ' ' || out[start] == '\t')) {
    ++start;
  }
  if (start > 0) out.erase(0, start);
  if (out.size() > 64) {
    size_t cut = 64;
    while (cut > 0 &&
           (static_cast<unsigned char>(out[cut]) & 0xC0) == 0x80) {
      --cut;
    }
    out.resize(cut);
  }
  return out;
}

// std::string из libc++ (ABI игры совпадает с NDK r28, как в BedrockTools):
//   короткая строка: byte0 = size << 1, данные с +1;
//   длинная: byte0 bit0 = 1, size по +8, указатель по +16.
bool readGameString(uintptr_t address, std::string &out) {
  char header[24]{};
  if (!mem::read(address, header, sizeof(header))) return false;
  const unsigned char b0 = static_cast<unsigned char>(header[0]);
  if ((b0 & 1u) != 0) {
    uint64_t size = 0;
    uintptr_t data = 0;
    std::memcpy(&size, header + 8, sizeof(size));
    std::memcpy(&data, header + 16, sizeof(data));
    if (size == 0 || size > 128 || !mem::isPlausiblePointer(data)) {
      return false;
    }
    out.resize(static_cast<size_t>(size));
    return mem::read(data, out.data(), out.size());
  }
  const size_t size = b0 >> 1;
  if (size > 22) return false;
  out.assign(header + 1, size);
  return true;
}

float normalizeFov(float value) {
  if (!std::isfinite(value) || value <= 0.0f) return 0.0f;
  if (value < 3.2f) return value;
  if (value >= 10.0f && value <= 179.0f) {
    return value * 0.01745329252f;
  }
  return 0.0f;
}

// Читает пару FOV и отсеивает неверные кандидаты по соотношению сторон.
bool readFovY(uintptr_t base, uintptr_t offsetX, uintptr_t offsetY,
              float aspect, float &out) {
  if (!base || !mem::isPlausiblePointer(base)) return false;
  float fx = 0.0f;
  float fy = 0.0f;
  if (!mem::read(base + offsetX, fx) || !mem::read(base + offsetY, fy)) {
    return false;
  }
  fx = normalizeFov(fx);
  fy = normalizeFov(fy);
  if (fx <= 0.0f || fy <= 0.0f) return false;
  const float ratio = std::tan(fx * 0.5f) / std::tan(fy * 0.5f);
  if (std::fabs(ratio - aspect) > aspect * 0.25f) return false;
  out = fy;
  return true;
}

Esp &Esp::instance() {
  static Esp esp;
  return esp;
}

bool Esp::initialize() {
  std::lock_guard<std::mutex> lock(m_initMutex);
  if (m_initialized.load(std::memory_order_acquire)) return true;

  if (!m_bridge.initialize()) {
    __android_log_print(ANDROID_LOG_WARN, kTag,
                        "ClientBridge init failed");
    return false;
  }

  const uintptr_t renderLevel =
      pl::memory::resolveSignature(offsets::kRenderLevelSignature,
                                   offsets::kMinecraftLibrary);
  const uintptr_t tessBegin =
      pl::memory::resolveSignature(offsets::kTessellatorBeginSignature,
                                   offsets::kMinecraftLibrary);
  const uintptr_t tessColor =
      pl::memory::resolveSignature(offsets::kTessellatorColorSignature,
                                   offsets::kMinecraftLibrary);
  const uintptr_t tessVertex =
      pl::memory::resolveSignature(offsets::kTessellatorVertexSignature,
                                   offsets::kMinecraftLibrary);
  const uintptr_t renderMesh =
      pl::memory::resolveSignature(offsets::kRenderMeshImmediatelySignature,
                                   offsets::kMinecraftLibrary);
  const uintptr_t getFov =
      pl::memory::resolveSignature(offsets::kGetFovSignature,
                                   offsets::kMinecraftLibrary);

  if (!validGameFn(renderLevel) || !validGameFn(tessBegin) ||
      !validGameFn(tessColor) || !validGameFn(tessVertex) ||
      !validGameFn(renderMesh)) {
    __android_log_print(
        ANDROID_LOG_WARN, kTag,
        "ESP signatures failed: render=%p begin=%p color=%p vertex=%p mesh=%p",
        reinterpret_cast<void *>(renderLevel),
        reinterpret_cast<void *>(tessBegin),
        reinterpret_cast<void *>(tessColor),
        reinterpret_cast<void *>(tessVertex),
        reinterpret_cast<void *>(renderMesh));
    return false;
  }

  g_tessBegin = reinterpret_cast<TessellatorBeginFn>(tessBegin);
  g_tessColor = reinterpret_cast<TessellatorColorFn>(tessColor);
  g_tessVertex = reinterpret_cast<TessellatorVertexFn>(tessVertex);
  g_renderMesh = reinterpret_cast<RenderMeshFn>(renderMesh);

  resolveMaterialGroup();

  if (pl::memory::hook(
          reinterpret_cast<pl::memory::FuncPtr>(renderLevel),
          reinterpret_cast<pl::memory::FuncPtr>(&renderLevelDetour),
          reinterpret_cast<pl::memory::FuncPtr *>(&g_renderLevelOriginal)) !=
      0) {
    __android_log_print(ANDROID_LOG_WARN, kTag, "renderLevel hook failed");
    return false;
  }

  if (validGameFn(getFov) &&
      pl::memory::hook(reinterpret_cast<pl::memory::FuncPtr>(getFov),
                       reinterpret_cast<pl::memory::FuncPtr>(&getFovDetour),
                       reinterpret_cast<pl::memory::FuncPtr *>(
                           &g_getFovOriginal)) == 0) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "GetFov hook=%p",
                        reinterpret_cast<void *>(getFov));
  } else {
    __android_log_print(ANDROID_LOG_WARN, kTag,
                        "GetFov signature/hook failed (fov fallback)");
  }

  m_initialized.store(true, std::memory_order_release);
  __android_log_print(ANDROID_LOG_INFO, kTag,
                      "ESP initialized: render=%p", (void *)renderLevel);
  return true;
}

void Esp::shutdown() {
  m_enabled.store(false, std::memory_order_release);
}

void Esp::setEnabled(bool enabled) {
  m_enabled.store(enabled, std::memory_order_release);
}

bool Esp::isEnabled() const {
  return m_enabled.load(std::memory_order_acquire);
}

void Esp::setPlayers(bool enabled) {
  m_showPlayers.store(enabled, std::memory_order_release);
}

void Esp::setMobs(bool enabled) {
  m_showMobs.store(enabled, std::memory_order_release);
}

void Esp::setRange(float range) {
  if (!std::isfinite(range)) return;
  m_range.store(std::clamp(range, 4.0f, 512.0f), std::memory_order_release);
}

void Esp::setPlayerColor(uint32_t argb) {
  m_playerColor.store(argb, std::memory_order_release);
}

void Esp::setMobColor(uint32_t argb) {
  m_mobColor.store(argb, std::memory_order_release);
}

void Esp::setArmoredColor(uint32_t argb) {
  m_armoredColor.store(argb, std::memory_order_relaxed);
}

void Esp::setArmorColors(bool enabled) {
  m_armorColors.store(enabled, std::memory_order_relaxed);
}

void Esp::setEspBoxStyle(int style) {
  m_espBoxStyle.store(std::clamp(style, 0, 4), std::memory_order_release);
}

int Esp::getEspBoxStyle() const {
  return m_espBoxStyle.load(std::memory_order_acquire);
}

std::vector<EspBoxLabel> Esp::copyEspBoxes() const {
  std::lock_guard<std::mutex> lock(m_boxMutex);
  return m_boxSnapshot;
}

void Esp::setNameTags(bool enabled) {
  m_nameTags.store(enabled, std::memory_order_release);
}

bool Esp::isNameTagsEnabled() const {
  return m_nameTags.load(std::memory_order_acquire);
}

void Esp::setNameTagSurface(float width, float height) {
  if (!std::isfinite(width) || !std::isfinite(height) || width <= 0.0f ||
      height <= 0.0f) {
    return;
  }
  m_surfaceWidth.store(width, std::memory_order_release);
  m_surfaceHeight.store(height, std::memory_order_release);
}

std::vector<NameTagLabel> Esp::copyNameTags() const {
  std::lock_guard<std::mutex> lock(m_nameMutex);
  return m_nameSnapshot;
}

void Esp::setTargetEsp(bool enabled) {
  m_targetEsp.store(enabled, std::memory_order_release);
  if (!enabled) {
    std::lock_guard<std::mutex> lock(m_targetMutex);
    m_targetEspActor = nullptr;
    m_targetEspSnapshot = TargetEspState{};
  }
}

bool Esp::isTargetEspEnabled() const {
  return m_targetEsp.load(std::memory_order_acquire);
}

void Esp::setTargetEspHoldMs(int holdMs) {
  m_targetEspHoldMs.store(std::clamp(holdMs, 120, 4000),
                          std::memory_order_release);
}

void Esp::markTargetEspHit(void *actor) {
  if (!actor) return;
  {
    std::lock_guard<std::mutex> lock(m_targetMutex);
    m_targetEspActor = actor;
    m_targetEspHit = std::chrono::steady_clock::now();
  }
}

TargetEspState Esp::copyTargetEsp() const {
  std::lock_guard<std::mutex> lock(m_targetMutex);
  return m_targetEspSnapshot;
}

namespace {
std::string normalizeFriendName(std::string name) {
  name = cleanPlayerName(std::move(name));
  size_t begin = 0;
  size_t end = name.size();
  while (begin < end &&
         (name[begin] == ' ' || name[begin] == '\t' ||
          name[begin] == '\r' || name[begin] == '\n')) {
    ++begin;
  }
  while (end > begin &&
         (name[end - 1] == ' ' || name[end - 1] == '\t' ||
          name[end - 1] == '\r' || name[end - 1] == '\n')) {
    --end;
  }
  std::string out = name.substr(begin, end - begin);
  for (char &c : out) {
    if (c >= 'A' && c <= 'Z') c = static_cast<char>(c - 'A' + 'a');
  }
  return out;
}
}  // namespace

void Esp::setFriendNames(const std::string &names) {
  std::unordered_set<std::string> parsed;
  size_t start = 0;
  for (size_t i = 0; i <= names.size(); ++i) {
    const bool end = i == names.size();
    const char c = end ? ',' : names[i];
    if (!end && c != ',' && c != ';' && c != '\n' && c != '\r' &&
        c != '\t') {
      continue;
    }
    std::string entry = normalizeFriendName(names.substr(start, i - start));
    if (!entry.empty()) parsed.insert(std::move(entry));
    start = i + 1;
  }
  std::lock_guard<std::mutex> lock(m_friendMutex);
  m_friends = std::move(parsed);
}

bool Esp::isFriend(void *actor) {
  if (!actor) return false;
  {
    std::lock_guard<std::mutex> lock(m_friendMutex);
    if (m_friends.empty()) return false;
  }
  const std::string name =
      normalizeFriendName(actorName(actor, std::chrono::steady_clock::now()));
  if (name.empty()) return false;
  std::lock_guard<std::mutex> lock(m_friendMutex);
  return m_friends.find(name) != m_friends.end();
}

std::string Esp::getActorName(void *actor) {
  if (!actor) return {};
  return actorName(actor, std::chrono::steady_clock::now());
}

std::string Esp::actorName(void *actor,
                           std::chrono::steady_clock::time_point now) {
  {
    std::lock_guard<std::mutex> lock(m_nameMutex);
    const auto it = m_nameCache.find(actor);
    if (it != m_nameCache.end() &&
        now - it->second.second < std::chrono::seconds(1)) {
      return it->second.first;
    }
  }

  const uintptr_t base = reinterpret_cast<uintptr_t>(actor);
  std::string name;
  if (readGameString(base + offsets::kPlayerName, name)) {
    name = cleanPlayerName(std::move(name));
  }
  if (name.empty() &&
      readGameString(base + offsets::kActorFilteredNameTag, name)) {
    name = cleanPlayerName(std::move(name));
  }
  if (name.empty()) {
    // Actor::getNameTag() is a real game call. If the actor list is momentarily
    // stale (e.g. right after a dimension teleport) the object is gone and the
    // call faults inside libminecraftpe. Only call it on a structurally valid
    // actor.
    const bool callable = [&]() {
      if (!mem::isPlausiblePointer(base)) return false;
      uintptr_t vtable = 0;
      if (!mem::read(base, vtable) ||
          !mem::isGameAddress(reinterpret_cast<const void *>(vtable))) {
        return false;
      }
      uintptr_t stateVector = 0;
      if (!mem::read(base + offsets::kActorStateVectorComponent, stateVector) ||
          !mem::isPlausiblePointer(stateVector)) {
        return false;
      }
      return true;
    }();
    if (callable) {
      if (ActorGetNameTagFn fn = actorGetNameTagFn()) {
        name = cleanPlayerName(fn(actor));
      }
    }
  }

  {
    std::lock_guard<std::mutex> lock(m_nameMutex);
    if (m_nameCache.size() > 4096) m_nameCache.clear();
    m_nameCache[actor] = {name, now};
  }
  return name;
}

uint32_t Esp::armorMaskFor(void *actor, std::chrono::steady_clock::time_point now) {
  {
    std::lock_guard<std::mutex> lock(m_metaMutex);
    const auto it = m_armorCache.find(actor);
    if (it != m_armorCache.end() &&
        now - it->second.second < std::chrono::milliseconds(400)) {
      return it->second.first;
    }
  }
  const uint32_t mask = gameecs::armorMaterialMask(actor);
  {
    std::lock_guard<std::mutex> lock(m_metaMutex);
    if (m_armorCache.size() > 4096) m_armorCache.clear();
    m_armorCache[actor] = {mask, now};
  }
  return mask;
}

std::string Esp::offhandFor(void *actor, std::chrono::steady_clock::time_point now) {
  {
    std::lock_guard<std::mutex> lock(m_metaMutex);
    const auto it = m_offhandCache.find(actor);
    if (it != m_offhandCache.end() &&
        now - it->second.second < std::chrono::milliseconds(400)) {
      return it->second.first;
    }
  }
  std::string name = gameecs::offhandItemName(actor);
  {
    std::lock_guard<std::mutex> lock(m_metaMutex);
    if (m_offhandCache.size() > 4096) m_offhandCache.clear();
    m_offhandCache[actor] = {name, now};
  }
  return name;
}

void Esp::updateNameTags(void *player, uintptr_t playerRenderer,
                         const float camera[3]) {
  std::vector<NameTagLabel> labels;

  const float width = m_surfaceWidth.load(std::memory_order_relaxed);
  const float height = m_surfaceHeight.load(std::memory_order_relaxed);
  ViewAngles view{};
  if (width > 1.0f && height > 1.0f &&
      m_bridge.getViewAngles(player, view)) {
    const float aspect = width / height;
    float fovY = 0.0f;
    const float liveFov = g_worldFovDeg.load(std::memory_order_relaxed);
    if (std::isfinite(liveFov) && liveFov > 1.0f && liveFov < 179.0f) {
      fovY = liveFov * 0.01745329252f;
    }
    if (fovY <= 0.0f) {
      const uintptr_t client =
          reinterpret_cast<uintptr_t>(m_bridge.getClientInstance());
      if (!readFovY(client, offsets::kClientInstanceFovX,
                    offsets::kClientInstanceFovY, aspect, fovY) &&
          !readFovY(playerRenderer, offsets::kLevelRendererPlayerFovX,
                    offsets::kLevelRendererPlayerFovY, aspect, fovY)) {
        fovY = 70.0f * 0.01745329252f;
      }
    }

    const float pitch = view.pitch * 0.01745329252f;
    const float yaw = view.yaw * 0.01745329252f;
    const float sy = std::sin(yaw);
    const float cy = std::cos(yaw);
    const float sp = std::sin(pitch);
    const float cp = std::cos(pitch);

    const float fx = -sy * cp;
    const float fy = -sp;
    const float fz = cy * cp;
    const float rx = -cy;
    const float rz = -sy;
    // up = right × forward
    const float ux = -rz * fy;
    const float uy = rz * fx - rx * fz;
    const float uz = rx * fy;
    const float focal = (height * 0.5f) / std::tan(fovY * 0.5f);

    const auto now = std::chrono::steady_clock::now();
    for (void *actor : m_actors) {
      if (!actor || actor == player) continue;
      if (!m_bridge.isPlayer(actor)) continue;

      Aabb bounds{};
      if (!m_bridge.getActorBounds(actor, bounds)) continue;

      const float dx = (bounds.min.x + bounds.max.x) * 0.5f - camera[0];
      const float dy = bounds.max.y + 0.12f - camera[1];
      const float dz = (bounds.min.z + bounds.max.z) * 0.5f - camera[2];
      const float depth = dx * fx + dy * fy + dz * fz;
      if (depth <= 0.15f) continue;

      const float right = dx * rx + dz * rz;
      const float up = dx * ux + dy * uy + dz * uz;
      const float labelX = width * 0.5f + (right / depth) * focal;
      const float labelY = height * 0.5f - (up / depth) * focal;
      if (labelX < -64.0f || labelX > width + 64.0f || labelY < -64.0f ||
          labelY > height + 64.0f) {
        continue;
      }

      std::string name = actorName(actor, now);
      if (name.empty()) continue;
      const uint32_t armorMask = armorMaskFor(actor, now);
      const int armorPieces = [armorMask]() {
        int count = 0;
        for (int slot = 0; slot < 4; ++slot) {
          if (((armorMask >> (slot * 8)) & 0xFFu) != 0) ++count;
        }
        return count;
      }();
      NameTagLabel label;
      label.x = labelX;
      label.y = labelY;
      label.distance = depth;
      label.armorPieces = armorPieces;
      label.armorMask = armorMask;
      label.health = gameecs::readHealth(actor);
      label.friendPlayer = isFriend(actor);
      label.offhandItem = offhandFor(actor, now);
      label.name = std::move(name);
      labels.push_back(std::move(label));
      if (labels.size() >= 256) break;
    }
  }

  std::lock_guard<std::mutex> lock(m_nameMutex);
  m_nameSnapshot = std::move(labels);
}

void Esp::updateEspBoxes(void *player, uintptr_t playerRenderer,
                         const float camera[3]) {
  std::vector<EspBoxLabel> boxes;
  const auto now = std::chrono::steady_clock::now();

  const float width = m_surfaceWidth.load(std::memory_order_relaxed);
  const float height = m_surfaceHeight.load(std::memory_order_relaxed);
  ViewAngles view{};
  if (width <= 1.0f || height <= 1.0f ||
      !m_bridge.getViewAngles(player, view)) {
    std::lock_guard<std::mutex> lock(m_boxMutex);
    m_boxSnapshot = std::move(boxes);
    return;
  }

  const float aspect = width / height;
  float fovY = 0.0f;
  const float liveFov = g_worldFovDeg.load(std::memory_order_relaxed);
  if (std::isfinite(liveFov) && liveFov > 1.0f && liveFov < 179.0f) {
    fovY = liveFov * 0.01745329252f;
  }
  if (fovY <= 0.0f) {
    const uintptr_t client =
        reinterpret_cast<uintptr_t>(m_bridge.getClientInstance());
    if (!readFovY(client, offsets::kClientInstanceFovX,
                  offsets::kClientInstanceFovY, aspect, fovY) &&
        !readFovY(playerRenderer, offsets::kLevelRendererPlayerFovX,
                  offsets::kLevelRendererPlayerFovY, aspect, fovY)) {
      fovY = 70.0f * 0.01745329252f;
    }
  }

  const float pitch = view.pitch * 0.01745329252f;
  const float yaw = view.yaw * 0.01745329252f;
  const float sy = std::sin(yaw);
  const float cy = std::cos(yaw);
  const float sp = std::sin(pitch);
  const float cp = std::cos(pitch);

  const float fx = -sy * cp;
  const float fy = -sp;
  const float fz = cy * cp;
  const float rx = -cy;
  const float rz = -sy;
  const float ux = -rz * fy;
  const float uy = rz * fx - rx * fz;
  const float uz = rx * fy;
  const float focal = (height * 0.5f) / std::tan(fovY * 0.5f);

  const bool showPlayers = m_showPlayers.load(std::memory_order_relaxed);
  const bool showMobs = m_showMobs.load(std::memory_order_relaxed);
  const bool armorColors = m_armorColors.load(std::memory_order_relaxed);
  const uint32_t playerColor = m_playerColor.load(std::memory_order_relaxed);
  const uint32_t mobColor = m_mobColor.load(std::memory_order_relaxed);
  const uint32_t armoredColor = m_armoredColor.load(std::memory_order_relaxed);

  for (void *actor : m_actors) {
    if (!actor || actor == player) continue;
    const bool isPlayer = m_bridge.isPlayer(actor);
    if (isPlayer ? !showPlayers : !showMobs) continue;
    if (isPlayer && AntiBot::instance().isBot(actor)) continue;

    Aabb bounds{};
    if (!m_bridge.getActorBounds(actor, bounds)) continue;

    float minX = 1e9f;
    float minY = 1e9f;
    float maxX = -1e9f;
    float maxY = -1e9f;
    bool anyBehind = false;
    for (int corner = 0; corner < 8; ++corner) {
      const float px = (corner & 1) ? bounds.max.x : bounds.min.x;
      const float py = (corner & 2) ? bounds.max.y : bounds.min.y;
      const float pz = (corner & 4) ? bounds.max.z : bounds.min.z;
      const float dx = px - camera[0];
      const float dy = py - camera[1];
      const float dz = pz - camera[2];
      const float depth = dx * fx + dy * fy + dz * fz;
      if (depth <= 0.15f) {
        anyBehind = true;
        break;
      }
      const float right = dx * rx + dz * rz;
      const float up = dx * ux + dy * uy + dz * uz;
      const float sx = width * 0.5f + (right / depth) * focal;
      const float sy2 = height * 0.5f - (up / depth) * focal;
      minX = std::min(minX, sx);
      minY = std::min(minY, sy2);
      maxX = std::max(maxX, sx);
      maxY = std::max(maxY, sy2);
    }
    if (anyBehind || minX >= maxX || minY >= maxY) continue;
    if (maxX < -32.0f || minX > width + 32.0f ||
        maxY < -32.0f || minY > height + 32.0f) {
      continue;
    }
    int armorPieces = 0;
    if (isPlayer) {
      const uint32_t armorMask = armorMaskFor(actor, now);
      for (int slot = 0; slot < 4; ++slot) {
        if (((armorMask >> (slot * 8)) & 0xFFu) != 0) ++armorPieces;
      }
    }
    uint32_t color =
        (isPlayer && armorColors && armorPieces > 0)
            ? armoredColor
            : (isPlayer ? playerColor : mobColor);
    if (isPlayer && isFriend(actor)) color = kFriendColor;
    boxes.push_back({minX, minY, maxX - minX, maxY - minY, color,
                     armorPieces});
    if (boxes.size() >= 256) break;
  }

  std::lock_guard<std::mutex> lock(m_boxMutex);
  m_boxSnapshot = std::move(boxes);
}

void Esp::updateTargetEsp(void *player, uintptr_t playerRenderer,
                          const float camera[3]) {
  TargetEspState state;
  void *target = nullptr;
  std::chrono::steady_clock::time_point hitTime{};
  {
    std::lock_guard<std::mutex> lock(m_targetMutex);
    target = m_targetEspActor;
    hitTime = m_targetEspHit;
  }

  const auto now = std::chrono::steady_clock::now();
  const int64_t holdMs = m_targetEspHoldMs.load(std::memory_order_relaxed);
  const int64_t ageMs =
      std::chrono::duration_cast<std::chrono::milliseconds>(now - hitTime)
          .count();

  bool targetKnown = false;
  for (void *actor : m_actors) {
    if (actor == target) {
      targetKnown = true;
      break;
    }
  }

  const float width = m_surfaceWidth.load(std::memory_order_relaxed);
  const float height = m_surfaceHeight.load(std::memory_order_relaxed);
  ViewAngles view{};
  if (targetKnown && target && ageMs >= 0 && ageMs <= holdMs &&
      width > 1.0f && height > 1.0f &&
      m_bridge.getViewAngles(player, view)) {
    Aabb bounds{};
    if (m_bridge.getActorBounds(target, bounds)) {
      const float aspect = width / height;
      float fovY = 0.0f;
      const float liveFov = g_worldFovDeg.load(std::memory_order_relaxed);
      if (std::isfinite(liveFov) && liveFov > 1.0f && liveFov < 179.0f) {
        fovY = liveFov * 0.01745329252f;
      }
      if (fovY <= 0.0f) {
        const uintptr_t client =
            reinterpret_cast<uintptr_t>(m_bridge.getClientInstance());
        if (!readFovY(client, offsets::kClientInstanceFovX,
                      offsets::kClientInstanceFovY, aspect, fovY) &&
            !readFovY(playerRenderer, offsets::kLevelRendererPlayerFovX,
                      offsets::kLevelRendererPlayerFovY, aspect, fovY)) {
          fovY = 70.0f * 0.01745329252f;
        }
      }

      const float pitch = view.pitch * 0.01745329252f;
      const float yaw = view.yaw * 0.01745329252f;
      const float sy = std::sin(yaw);
      const float cy = std::cos(yaw);
      const float sp = std::sin(pitch);
      const float cp = std::cos(pitch);

      const float fx = -sy * cp;
      const float fy = -sp;
      const float fz = cy * cp;
      const float rx = -cy;
      const float rz = -sy;
      const float ux = -rz * fy;
      const float uy = rz * fx - rx * fz;
      const float uz = rx * fy;
      const float focal = (height * 0.5f) / std::tan(fovY * 0.5f);

      float minX = 1e9f;
      float minY = 1e9f;
      float maxX = -1e9f;
      float maxY = -1e9f;
      bool anyBehind = false;
      for (int corner = 0; corner < 8; ++corner) {
        const float px = (corner & 1) ? bounds.max.x : bounds.min.x;
        const float py = (corner & 2) ? bounds.max.y : bounds.min.y;
        const float pz = (corner & 4) ? bounds.max.z : bounds.min.z;
        const float dx = px - camera[0];
        const float dy = py - camera[1];
        const float dz = pz - camera[2];
        const float depth = dx * fx + dy * fy + dz * fz;
        if (depth <= 0.15f) {
          anyBehind = true;
          break;
        }
        const float right = dx * rx + dz * rz;
        const float up = dx * ux + dy * uy + dz * uz;
        const float sx = width * 0.5f + (right / depth) * focal;
        const float sy2 = height * 0.5f - (up / depth) * focal;
        minX = std::min(minX, sx);
        minY = std::min(minY, sy2);
        maxX = std::max(maxX, sx);
        maxY = std::max(maxY, sy2);
      }
      if (!anyBehind && minX < maxX && minY < maxY) {
        state.visible = true;
        state.x = minX;
        state.y = minY;
        state.width = maxX - minX;
        state.height = maxY - minY;
        state.ageMs = std::max<int64_t>(0, ageMs);
        state.yaw = view.yaw;
        state.camX = camera[0];
        state.camY = camera[1];
        state.camZ = camera[2];
        state.minX = bounds.min.x;
        state.minY = bounds.min.y;
        state.minZ = bounds.min.z;
        state.maxX = bounds.max.x;
        state.maxY = bounds.max.y;
        state.maxZ = bounds.max.z;
        state.fx = fx;
        state.fy = fy;
        state.fz = fz;
        state.rx = rx;
        state.rz = rz;
        state.ux = ux;
        state.uy = uy;
        state.uz = uz;
        state.focal = focal;
        state.screenW = width;
        state.screenH = height;
      }
    }
  }

  if (!state.visible && (ageMs > holdMs || !targetKnown)) {
    std::lock_guard<std::mutex> lock(m_targetMutex);
    m_targetEspActor = nullptr;
  }

  std::lock_guard<std::mutex> lock(m_targetMutex);
  m_targetEspSnapshot = state;
}

void Esp::render(void *levelRenderer, void *screenContext) {
  if (!m_initialized.load(std::memory_order_acquire)) return;
  if (!mem::isPlausiblePointer(reinterpret_cast<uintptr_t>(levelRenderer))) {
    return;
  }

  const bool espBoxes = m_enabled.load(std::memory_order_acquire);
  const bool showPlayers = m_showPlayers.load(std::memory_order_relaxed);
  const bool showMobs = m_showMobs.load(std::memory_order_relaxed);
  const bool nameTags = m_nameTags.load(std::memory_order_relaxed);
  const bool targetEsp = m_targetEsp.load(std::memory_order_relaxed);
  const int boxStyle = m_espBoxStyle.load(std::memory_order_relaxed);
  const bool javaBoxes = boxStyle > 0;
  if (!espBoxes && !nameTags && !targetEsp) return;
  if (espBoxes && !showPlayers && !showMobs && !nameTags && !targetEsp) return;

  void *player = m_bridge.getLocalPlayer();
  if (!player) return;

  uintptr_t level = reinterpret_cast<uintptr_t>(levelRenderer);
  uintptr_t playerRenderer = 0;
  if (!mem::read(level + offsets::kLevelRendererPlayer, playerRenderer) ||
      !mem::isPlausiblePointer(playerRenderer)) {
    return;
  }

  float camera[3]{};
  if (!mem::read(playerRenderer + offsets::kLevelRendererPlayerCamPos, camera)) {
    return;
  }
  if (!std::isfinite(camera[0]) || !std::isfinite(camera[1]) ||
      !std::isfinite(camera[2])) {
    return;
  }

  const float scanRange = targetEsp
      ? std::max(m_range.load(std::memory_order_relaxed), 64.0f)
      : m_range.load(std::memory_order_relaxed);

  // Actor scan + snapshots ~30 Hz. The scan (game fetchNearbyActors) is the
  // single most expensive per-frame call; isPlayer is fault-guarded and
  // getActorBounds uses safe reads, so a slightly stale list can never fault.
  const auto boxNow = std::chrono::steady_clock::now();
  if (m_actors.empty() ||
      boxNow - m_lastBoxCompute >= std::chrono::milliseconds(33)) {
    m_lastBoxCompute = boxNow;
    m_bridge.fillNearbyActors(player, scanRange, m_actors);
    AntiBot::instance().update(m_actors, player);
    if (nameTags) updateNameTags(player, playerRenderer, camera);
    if (espBoxes && javaBoxes) updateEspBoxes(player, playerRenderer, camera);
    if (targetEsp) updateTargetEsp(player, playerRenderer, camera);
  }
  if (!espBoxes || javaBoxes) return;

  if (!mem::isPlausiblePointer(reinterpret_cast<uintptr_t>(screenContext))) {
    return;
  }
  uintptr_t screen = reinterpret_cast<uintptr_t>(screenContext);
  uintptr_t tessellator = 0;
  if (!mem::read(screen + offsets::kScreenContextTessellator, tessellator) ||
      !mem::isPlausiblePointer(tessellator)) {
    return;
  }
  uintptr_t probe = 0;
  if (!mem::read(tessellator, probe)) return;

  ensureXrayMaterial();
  void *material =
      g_xrayMaterial
          ? static_cast<void *>(&g_xrayMaterial)
          : reinterpret_cast<void *>(playerRenderer +
                                     offsets::kLevelRendererSelectionMaterial);

  ColorState colorState(screenContext);
  if (!colorState) return;

  const uint32_t playerColor = m_playerColor.load(std::memory_order_relaxed);
  const uint32_t mobColor = m_mobColor.load(std::memory_order_relaxed);
  const uint32_t armoredColor =
      m_armoredColor.load(std::memory_order_relaxed);
  const bool armorColors = m_armorColors.load(std::memory_order_relaxed);

  int drawn = 0;
  for (void *actor : m_actors) {
    if (!actor || actor == player) continue;

    const bool isPlayer = m_bridge.isPlayer(actor);
    if (isPlayer && !showPlayers) continue;
    if (isPlayer && AntiBot::instance().isBot(actor)) continue;
    if (!isPlayer) {
      if (!showMobs) continue;
      const uint32_t categories = m_bridge.getActorCategories(actor);
      if ((categories & offsets::kMobCategory) == 0) continue;
    }

    Aabb bounds{};
    if (!m_bridge.getActorBounds(actor, bounds)) continue;
    uint32_t color = mobColor;
    if (isPlayer) {
      color = (armorColors && gameecs::hasArmor(actor)) ? armoredColor
                                                       : playerColor;
      if (isFriend(actor)) color = kFriendColor;
    }
    drawBox(reinterpret_cast<void *>(tessellator), screenContext, material,
            bounds, camera, color);
    ++drawn;
  }

  const auto now = std::chrono::steady_clock::now();
  if (now - m_lastLog >= std::chrono::seconds(5)) {
    m_lastLog = now;
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "ESP frame: actors=%zu drawn=%d material=%s",
                        m_actors.size(), drawn,
                        g_xrayMaterialName ? g_xrayMaterialName
                                           : "selection_overlay(lrp)");
  }
}

} // namespace aimbot
