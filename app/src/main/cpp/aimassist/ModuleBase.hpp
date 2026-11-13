#pragma once

// Минимальный базовый класс модуля.
// Если в твоём клиенте есть свой Module/ModuleBase — просто унаследуй
// AimAssist от него и удали этот файл; набор методов совпадает.
namespace aimbot {

class ModuleBase {
public:
  virtual ~ModuleBase() = default;

  virtual void onEnable() {}
  virtual void onDisable() {}
  virtual void onTick() {}
  virtual void onRender(float dt) { (void)dt; }
  virtual void onConfigLoad() {}
};

} // namespace aimbot
