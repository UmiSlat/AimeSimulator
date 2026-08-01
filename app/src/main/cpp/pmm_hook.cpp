#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <sys/system_properties.h>

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <vector>

#include <dobby.h>

namespace {

constexpr char kLogTag[] = "AimePmmPatch";
constexpr std::array<std::uint8_t, 8> kPmm = {0x00, 0xF1, 0x00, 0x00, 0x00, 0x01, 0x43, 0x00};
constexpr char kLegacySymbol[] = "_Z23nfa_dm_check_set_confighPhb";
constexpr char kVendorSymbol[] = "_Z17HalSendDownstreamPvPKhm";

using LegacyFunction = void (*)(std::uint8_t, std::uint8_t*, bool);
using VendorFunction = int (*)(void*, const std::uint8_t*, std::size_t);

LegacyFunction originalLegacy = nullptr;
VendorFunction originalVendor = nullptr;
std::once_flag installOnce;

void patchParameter(std::uint8_t identifier, std::uint8_t* lengthAndValue) {
    if (lengthAndValue == nullptr) return;
    const std::size_t length = lengthAndValue[0];
    std::uint8_t* value = lengthAndValue + 1;
    if (identifier == 0x51 && length >= kPmm.size()) {
        std::copy(kPmm.begin(), kPmm.end(), value);
    } else if (identifier == 0x40 && length >= 0x12) {
        std::copy(kPmm.begin(), kPmm.end(), value + length - kPmm.size());
    }
}

void patchCoreSetConfig(std::uint8_t* packet, std::size_t size) {
    if (packet == nullptr || size < 5 || packet[0] != 0x20 || packet[1] != 0x02) return;
    const std::size_t payloadEnd = std::min(size, static_cast<std::size_t>(3 + packet[2]));
    if (payloadEnd <= 4) return;
    std::size_t cursor = 4;
    while (cursor + 2 <= payloadEnd) {
        const std::uint8_t identifier = packet[cursor];
        const std::size_t length = packet[cursor + 1];
        if (cursor + 2 + length > payloadEnd) break;
        patchParameter(identifier, packet + cursor + 1);
        cursor += 2 + length;
    }
}

void replacementLegacy(std::uint8_t identifier, std::uint8_t* lengthAndValue, bool local) {
    patchParameter(identifier, lengthAndValue);
    originalLegacy(identifier, lengthAndValue, local);
}

int replacementVendor(void* context, const std::uint8_t* packet, std::size_t size) {
    if (packet == nullptr || size == 0) return originalVendor(context, packet, size);
    std::vector<std::uint8_t> mutablePacket(packet, packet + size);
    patchCoreSetConfig(mutablePacket.data(), mutablePacket.size());
    return originalVendor(context, mutablePacket.data(), mutablePacket.size());
}

bool hookSymbol(const char* name, void* replacement, void** original) {
    void* address = dlsym(RTLD_DEFAULT, name);
    if (address == nullptr) return false;
    const int status = DobbyHook(address, replacement, original);
    __android_log_print(status == 0 ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR, kLogTag,
                        "hook %s: %d", name, status);
    return status == 0;
}

void installHooks() {
    std::call_once(installOnce, [] {
        bool installed = hookSymbol(kVendorSymbol, reinterpret_cast<void*>(replacementVendor),
                                    reinterpret_cast<void**>(&originalVendor));
        installed = hookSymbol(kLegacySymbol, reinterpret_cast<void*>(replacementLegacy),
                               reinterpret_cast<void**>(&originalLegacy)) || installed;
        if (installed) __system_property_set("tmp.aimesim.pmm.active", "true");
        else __android_log_print(ANDROID_LOG_WARN, kLogTag, "no supported NFC symbol was found");
    });
}

}  // namespace

__attribute__((constructor)) static void onLibraryLoaded() {
    installHooks();
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    installHooks();
    return JNI_VERSION_1_6;
}
