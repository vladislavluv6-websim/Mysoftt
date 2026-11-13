#include <jni.h>

#include <android/log.h>

#include <cstdio>
#include <string>
#include <vector>

#include "DiagLog.hpp"

#include "AimAssist.hpp"
#include "ClientBridge.hpp"
#include "Esp.hpp"
#include "Fullbright.hpp"

namespace {

constexpr const char *kTag = "AimAssistJni";

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetDiagLogPath(
    JNIEnv *env, jclass, jstring path) {
  if (!path) {
    aimbot::setDiagLogPath(nullptr);
    return;
  }
  const char *chars = env->GetStringUTFChars(path, nullptr);
  if (chars) {
    aimbot::setDiagLogPath(chars);
    env->ReleaseStringUTFChars(path, chars);
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeInit(
    JNIEnv *, jclass) {
  const bool ok = aimbot::ClientBridge::instance().initialize();
  if (ok) {
    aimbot::AimAssist::instance().onConfigLoad();
  }
  aimbot::diagLog(kTag, "nativeInit=%d", ok ? 1 : 0);
  return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetEnabled(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setEnabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeIsEnabled(
    JNIEnv *, jclass) {
  return aimbot::AimAssist::instance().isEnabled() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetRange(
    JNIEnv *, jclass, jfloat range) {
  aimbot::AimAssist::instance().setRange(range);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetStrength(
    JNIEnv *, jclass, jfloat strength) {
  aimbot::AimAssist::instance().setStrength(strength);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetFov(
    JNIEnv *, jclass, jfloat fovDeg) {
  aimbot::AimAssist::instance().setFov(fovDeg);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetWallCheck(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setWallCheck(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetIgnoreInvisible(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setIgnoreInvisible(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetNeuralRotation(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setNeuralRotation(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetKillaura(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setKillaura(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetKillauraConeDeg(
    JNIEnv *, jclass, jfloat deg) {
  aimbot::AimAssist::instance().setKillauraConeDeg(deg);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetKillauraReactionMs(
    JNIEnv *, jclass, jfloat ms) {
  aimbot::AimAssist::instance().setKillauraReactionMs(ms);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetRotationRecording(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setRotationRecording(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetRotationPatternPath(
    JNIEnv *env, jclass, jstring path) {
  if (!path) {
    aimbot::AimAssist::instance().setRotationPatternPath("");
    return;
  }
  const char *chars = env->GetStringUTFChars(path, nullptr);
  if (chars) {
    aimbot::AimAssist::instance().setRotationPatternPath(chars);
    env->ReleaseStringUTFChars(path, chars);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetTargetPlayers(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setTargetPlayers(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetTargetMobs(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setTargetMobs(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetLegitMode(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setLegitMode(enabled == JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetBodyPart(
    JNIEnv *, jclass, jint bodyPart) {
  aimbot::AimAssist::instance().setBodyPart(bodyPart);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetMaxSpeed(
    JNIEnv *, jclass, jfloat degPerSecond) {
  aimbot::AimAssist::instance().setMaxSpeed(degPerSecond);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetSmoothing(
    JNIEnv *, jclass, jfloat speed) {
  aimbot::AimAssist::instance().setSmoothing(speed);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetAcceleration(
    JNIEnv *, jclass, jfloat degPerSecond2) {
  aimbot::AimAssist::instance().setAcceleration(degPerSecond2);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetDeadzone(
    JNIEnv *, jclass, jfloat deg) {
  aimbot::AimAssist::instance().setDeadzone(deg);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetFullSpeedRange(
    JNIEnv *, jclass, jfloat deg) {
  aimbot::AimAssist::instance().setFullSpeedRange(deg);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetStickyTicks(
    JNIEnv *, jclass, jint ticks) {
  aimbot::AimAssist::instance().setStickyTicks(ticks);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetTargetLock(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setTargetLock(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetFastAim(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setFastAim(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetFastAimTime(
    JNIEnv *, jclass, jfloat timeMs) {
  aimbot::AimAssist::instance().setFastAimTime(timeMs);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetFastAimMaxSpeed(
    JNIEnv *, jclass, jfloat degPerSecond) {
  aimbot::AimAssist::instance().setFastAimMaxSpeed(degPerSecond);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetSwingStyle(
    JNIEnv *, jclass, jint style) {
  aimbot::ClientBridge::instance().setSwingStyle(style);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetSwingSpeed(
    JNIEnv *, jclass, jint percent) {
  aimbot::ClientBridge::instance().setSwingSpeed(percent);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetSwingAmplitude(
    JNIEnv *, jclass, jint percent) {
  aimbot::ClientBridge::instance().setSwingAmplitude(percent);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetTargetGrace(
    JNIEnv *, jclass, jint frames) {
  aimbot::AimAssist::instance().setTargetGrace(frames);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetPreferClosest(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setPreferClosest(enabled == JNI_TRUE);
}


extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetTriggerBot(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setTriggerBot(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetTriggerDelay(
    JNIEnv *, jclass, jint delayMs) {
  aimbot::AimAssist::instance().setTriggerDelay(delayMs);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetTriggerInterval(
    JNIEnv *, jclass, jint intervalMs) {
  aimbot::AimAssist::instance().setTriggerInterval(intervalMs);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetTriggerRange(
    JNIEnv *, jclass, jfloat range) {
  aimbot::AimAssist::instance().setTriggerRange(range);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetTriggerTolerance(
    JNIEnv *, jclass, jfloat tolerance) {
  aimbot::AimAssist::instance().setTriggerTolerance(tolerance);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetHumanization(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setHumanization(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetReactionDelay(
    JNIEnv *, jclass, jint baseMs, jint jitterMs) {
  aimbot::AimAssist::instance().setReactionDelay(baseMs, jitterMs);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetAimTremor(
    JNIEnv *, jclass, jfloat degrees) {
  aimbot::AimAssist::instance().setAimTremor(degrees);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetAntiCheatMode(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setAntiCheatMode(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetAssistCone(
    JNIEnv *, jclass, jfloat degrees) {
  aimbot::AimAssist::instance().setAssistCone(degrees);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetAttackMissChance(
    JNIEnv *, jclass, jfloat chance) {
  aimbot::AimAssist::instance().setAttackMissChance(chance);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetServerViewGuard(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setServerViewGuard(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetSustainedTickDelta(
    JNIEnv *, jclass, jfloat degrees) {
  aimbot::AimAssist::instance().setSustainedTickDelta(degrees);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetOnlyCrit(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setOnlyCrit(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetAntiBot(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setAntiBot(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetRandomAimPoint(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::AimAssist::instance().setRandomAimPoint(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeIsOnTarget(
    JNIEnv *, jclass) {
  return aimbot::AimAssist::instance().isOnTarget() ? JNI_TRUE : JNI_FALSE;
}

// Дополнительные точки входа для input-слоя клиента.
extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetTouchActive(
    JNIEnv *, jclass, jboolean active) {
  aimbot::AimAssist::instance().setTouchActive(active == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeNotifyCameraLook(
    JNIEnv *, jclass) {
  aimbot::AimAssist::instance().notifyCameraLook();
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_AimAssistMod_nativeSetActivationHeld(
    JNIEnv *, jclass, jboolean held) {
  aimbot::AimAssist::instance().setActivationHeld(held == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeInit(
    JNIEnv *, jclass) {
  const bool ok = aimbot::Esp::instance().initialize();
  __android_log_print(ANDROID_LOG_INFO, "AimAssistJni", "espInit=%d", ok ? 1 : 0);
  return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetEnabled(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::Esp::instance().setEnabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetPlayers(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::Esp::instance().setPlayers(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetMobs(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::Esp::instance().setMobs(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetRange(
    JNIEnv *, jclass, jfloat range) {
  aimbot::Esp::instance().setRange(range);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetPlayerColor(
    JNIEnv *, jclass, jint argb) {
  aimbot::Esp::instance().setPlayerColor(static_cast<uint32_t>(argb));
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetMobColor(
    JNIEnv *, jclass, jint argb) {
  aimbot::Esp::instance().setMobColor(static_cast<uint32_t>(argb));
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetArmoredColor(
    JNIEnv *, jclass, jint argb) {
  aimbot::Esp::instance().setArmoredColor(static_cast<uint32_t>(argb));
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetArmorColors(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::Esp::instance().setArmorColors(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetEspBoxStyle(
    JNIEnv *, jclass, jint style) {
  aimbot::Esp::instance().setEspBoxStyle(style);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeGetEspBoxes(
    JNIEnv *env, jclass) {
  const std::vector<aimbot::EspBoxLabel> boxes =
      aimbot::Esp::instance().copyEspBoxes();
  if (boxes.empty() || !env) return nullptr;

  jclass stringClass = env->FindClass("java/lang/String");
  if (!stringClass) return nullptr;

  jobjectArray result =
      env->NewObjectArray(static_cast<jsize>(boxes.size()), stringClass, nullptr);
  if (!result) {
    env->DeleteLocalRef(stringClass);
    return nullptr;
  }

  for (size_t i = 0; i < boxes.size(); ++i) {
    char entry[96]{};
    std::snprintf(entry, sizeof(entry),
                  "%.1f\t%.1f\t%.1f\t%.1f\t%u\t%d",
                  boxes[i].x, boxes[i].y, boxes[i].width, boxes[i].height,
                  static_cast<unsigned>(boxes[i].color),
                  boxes[i].armorPieces);
    jstring item = env->NewStringUTF(entry);
    if (item) {
      env->SetObjectArrayElement(result, static_cast<jsize>(i), item);
      env->DeleteLocalRef(item);
    }
  }
  env->DeleteLocalRef(stringClass);
  return result;
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetNameTags(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::Esp::instance().setNameTags(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetNameTagScreenSize(
    JNIEnv *, jclass, jfloat width, jfloat height) {
  aimbot::Esp::instance().setNameTagSurface(width, height);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeGetNameTags(
    JNIEnv *env, jclass) {
  const std::vector<aimbot::NameTagLabel> labels =
      aimbot::Esp::instance().copyNameTags();
  if (labels.empty() || !env) return nullptr;

  jclass stringClass = env->FindClass("java/lang/String");
  if (!stringClass) return nullptr;

  jobjectArray result =
      env->NewObjectArray(static_cast<jsize>(labels.size()), stringClass, nullptr);
  if (!result) {
    env->DeleteLocalRef(stringClass);
    return nullptr;
  }

  for (size_t i = 0; i < labels.size(); ++i) {
    char coordinates[96]{};
    std::snprintf(coordinates, sizeof(coordinates),
                  "%.1f	%.1f	%.1f	%d	%.1f	%08X	%d	",
                  labels[i].x, labels[i].y, labels[i].distance,
                  labels[i].armorPieces, labels[i].health,
                  labels[i].armorMask, labels[i].friendPlayer ? 1 : 0);
    const std::string entry = std::string(coordinates) +
                              labels[i].offhandItem + "\t" + labels[i].name;
    jstring item = env->NewStringUTF(entry.c_str());
    if (item) {
      env->SetObjectArrayElement(result, static_cast<jsize>(i), item);
      env->DeleteLocalRef(item);
    }
  }

  env->DeleteLocalRef(stringClass);
  return result;
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetTargetEsp(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::Esp::instance().setTargetEsp(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetTargetEspHold(
    JNIEnv *, jclass, jint holdMs) {
  aimbot::Esp::instance().setTargetEspHoldMs(static_cast<int>(holdMs));
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeSetFriendNames(
    JNIEnv *env, jclass, jstring names) {
  if (!names) {
    aimbot::Esp::instance().setFriendNames("");
    return;
  }
  const char *chars = env->GetStringUTFChars(names, nullptr);
  if (chars) {
    aimbot::Esp::instance().setFriendNames(chars);
    env->ReleaseStringUTFChars(names, chars);
  }
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_EspMod_nativeGetTargetEsp(
    JNIEnv *env, jclass) {
  const aimbot::TargetEspState state =
      aimbot::Esp::instance().copyTargetEsp();
  if (!state.visible || !env) return nullptr;
  char buffer[640]{};
  std::snprintf(
      buffer, sizeof(buffer),
      "%.1f\t%.1f\t%.1f\t%.1f\t%lld\t%.1f\t"
      "%.3f\t%.3f\t%.3f\t"
      "%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t"
      "%.5f\t%.5f\t%.5f\t%.5f\t%.5f\t%.5f\t%.5f\t%.5f\t%.2f\t"
      "%.1f\t%.1f",
      static_cast<double>(state.x), static_cast<double>(state.y),
      static_cast<double>(state.width), static_cast<double>(state.height),
      static_cast<long long>(state.ageMs), static_cast<double>(state.yaw),
      static_cast<double>(state.camX), static_cast<double>(state.camY),
      static_cast<double>(state.camZ), static_cast<double>(state.minX),
      static_cast<double>(state.minY), static_cast<double>(state.minZ),
      static_cast<double>(state.maxX), static_cast<double>(state.maxY),
      static_cast<double>(state.maxZ), static_cast<double>(state.fx),
      static_cast<double>(state.fy), static_cast<double>(state.fz),
      static_cast<double>(state.rx), static_cast<double>(state.rz),
      static_cast<double>(state.ux), static_cast<double>(state.uy),
      static_cast<double>(state.uz), static_cast<double>(state.focal),
      static_cast<double>(state.screenW), static_cast<double>(state.screenH));
  return env->NewStringUTF(buffer);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_FullbrightMod_nativeInit(
    JNIEnv *, jclass) {
  const bool ok = aimbot::Fullbright::instance().initialize();
  __android_log_print(ANDROID_LOG_INFO, "AimAssistJni", "fullbrightInit=%d",
                      ok ? 1 : 0);
  return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_FullbrightMod_nativeSetEnabled(
    JNIEnv *, jclass, jboolean enabled) {
  aimbot::Fullbright::instance().setEnabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_FullbrightMod_nativeIsEnabled(
    JNIEnv *, jclass) {
  return aimbot::Fullbright::instance().isEnabled() ? JNI_TRUE : JNI_FALSE;
}
