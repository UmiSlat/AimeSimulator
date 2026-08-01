#define _GNU_SOURCE

#include <asm/ptrace.h>
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <signal.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/ptrace.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <sys/wait.h>
#include <unistd.h>

struct mapping {
    uintptr_t start;
    uintptr_t end;
    unsigned long offset;
    char permissions[5];
    char path[PATH_MAX];
};

static int read_mapping(pid_t pid, const char* contains, bool executable, struct mapping* output) {
    char mapsPath[64];
    snprintf(mapsPath, sizeof(mapsPath), "/proc/%d/maps", pid);
    FILE* maps = fopen(mapsPath, "re");
    if (maps == NULL) return -1;
    char line[PATH_MAX + 160];
    int found = -1;
    while (fgets(line, sizeof(line), maps) != NULL) {
        struct mapping candidate = {0};
        char path[PATH_MAX] = {0};
        int fields = sscanf(line, "%lx-%lx %4s %lx %*s %*s %s",
                            &candidate.start, &candidate.end, candidate.permissions,
                            &candidate.offset, path);
        if (fields < 4) continue;
        if (contains != NULL && (fields < 5 || strstr(path, contains) == NULL)) continue;
        if (executable && strchr(candidate.permissions, 'x') == NULL) continue;
        if (fields >= 5) snprintf(candidate.path, sizeof(candidate.path), "%s", path);
        *output = candidate;
        found = 0;
        break;
    }
    fclose(maps);
    return found;
}

static uintptr_t module_load_bias(pid_t pid, const char* contains, char path[PATH_MAX]) {
    char mapsPath[64];
    snprintf(mapsPath, sizeof(mapsPath), "/proc/%d/maps", pid);
    FILE* maps = fopen(mapsPath, "re");
    if (maps == NULL) return 0;
    char line[PATH_MAX + 160];
    uintptr_t bias = 0;
    while (fgets(line, sizeof(line), maps) != NULL) {
        uintptr_t start = 0, end = 0;
        unsigned long offset = 0;
        char permissions[5] = {0};
        char mappedPath[PATH_MAX] = {0};
        int fields = sscanf(line, "%lx-%lx %4s %lx %*s %*s %s",
                            &start, &end, permissions, &offset, mappedPath);
        if (fields == 5 && strstr(mappedPath, contains) != NULL && offset == 0) {
            bias = start;
            snprintf(path, PATH_MAX, "%s", mappedPath);
            break;
        }
    }
    fclose(maps);
    return bias;
}

static uintptr_t dynamic_symbol_value(const char* filePath, const char* requested) {
    int fd = open(filePath, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return 0;
    struct stat info;
    if (fstat(fd, &info) != 0 || info.st_size < (off_t)sizeof(Elf64_Ehdr)) {
        close(fd);
        return 0;
    }
    void* bytes = mmap(NULL, (size_t)info.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (bytes == MAP_FAILED) return 0;

    uintptr_t value = 0;
    const Elf64_Ehdr* header = bytes;
    if (memcmp(header->e_ident, ELFMAG, SELFMAG) == 0 && header->e_ident[EI_CLASS] == ELFCLASS64 &&
        header->e_shoff + (uint64_t)header->e_shnum * header->e_shentsize <= (uint64_t)info.st_size) {
        const Elf64_Shdr* sections = (const Elf64_Shdr*)((const uint8_t*)bytes + header->e_shoff);
        for (uint16_t index = 0; index < header->e_shnum; ++index) {
            if (sections[index].sh_type != SHT_DYNSYM || sections[index].sh_link >= header->e_shnum) continue;
            const Elf64_Shdr* strings = &sections[sections[index].sh_link];
            if (sections[index].sh_offset + sections[index].sh_size > (uint64_t)info.st_size ||
                strings->sh_offset + strings->sh_size > (uint64_t)info.st_size) continue;
            const Elf64_Sym* symbols = (const Elf64_Sym*)((const uint8_t*)bytes + sections[index].sh_offset);
            const char* names = (const char*)bytes + strings->sh_offset;
            size_t count = sections[index].sh_size / sizeof(Elf64_Sym);
            for (size_t symbol = 0; symbol < count; ++symbol) {
                if (symbols[symbol].st_name < strings->sh_size &&
                    strcmp(names + symbols[symbol].st_name, requested) == 0) {
                    value = symbols[symbol].st_value;
                    break;
                }
            }
            if (value != 0) break;
        }
    }
    munmap(bytes, (size_t)info.st_size);
    return value;
}

static int get_registers(pid_t pid, struct user_pt_regs* registers) {
    struct iovec vector = {.iov_base = registers, .iov_len = sizeof(*registers)};
    return ptrace(PTRACE_GETREGSET, pid, (void*)NT_PRSTATUS, &vector);
}

static int set_registers(pid_t pid, const struct user_pt_regs* registers) {
    struct iovec vector = {.iov_base = (void*)registers, .iov_len = sizeof(*registers)};
    return ptrace(PTRACE_SETREGSET, pid, (void*)NT_PRSTATUS, &vector);
}

static int write_remote(pid_t pid, uintptr_t address, const void* source, size_t length) {
    const uint8_t* input = source;
    for (size_t offset = 0; offset < length; offset += sizeof(long)) {
        long word = 0;
        size_t chunk = length - offset < sizeof(long) ? length - offset : sizeof(long);
        if (chunk != sizeof(long)) {
            errno = 0;
            word = ptrace(PTRACE_PEEKDATA, pid, (void*)(address + offset), NULL);
            if (word == -1 && errno != 0) return -1;
        }
        memcpy(&word, input + offset, chunk);
        if (ptrace(PTRACE_POKEDATA, pid, (void*)(address + offset), (void*)word) != 0) return -1;
    }
    return 0;
}

static int wait_for_stop(pid_t pid, int expectedSignal) {
    for (int attempt = 0; attempt < 100; ++attempt) {
        int status = 0;
        pid_t result = waitpid(pid, &status, __WALL | WNOHANG);
        if (result == pid) {
            if (!WIFSTOPPED(status)) return -1;
            return expectedSignal == 0 || WSTOPSIG(status) == expectedSignal ? 0 : -1;
        }
        if (result < 0) return -1;
        usleep(50000);
    }
    errno = ETIMEDOUT;
    return -1;
}

static int call_remote_dlopen(pid_t pid, uintptr_t function, uintptr_t caller,
                              const char* libraryPath, uintptr_t* handle) {
    struct user_pt_regs saved;
    if (get_registers(pid, &saved) != 0) return -1;
    struct user_pt_regs call = saved;
    uintptr_t remoteString = (saved.sp - strlen(libraryPath) - 32) & ~(uintptr_t)0xF;

    errno = 0;
    long originalInstruction = ptrace(PTRACE_PEEKDATA, pid, (void*)saved.pc, NULL);
    if (originalInstruction == -1 && errno != 0) return -1;
    long trapInstruction = originalInstruction;
    const uint32_t brk = 0xD4200000;
    memcpy(&trapInstruction, &brk, sizeof(brk));

    if (write_remote(pid, remoteString, libraryPath, strlen(libraryPath) + 1) != 0 ||
        ptrace(PTRACE_POKEDATA, pid, (void*)saved.pc, (void*)trapInstruction) != 0) return -1;

    call.regs[0] = remoteString;
    call.regs[1] = RTLD_NOW;
    call.regs[2] = caller;
    call.sp = remoteString;
    call.pc = function;
    call.regs[30] = saved.pc;
    int result = -1;
    if (set_registers(pid, &call) == 0 && ptrace(PTRACE_CONT, pid, NULL, NULL) == 0 &&
        wait_for_stop(pid, SIGTRAP) == 0 && get_registers(pid, &call) == 0) {
        *handle = call.regs[0];
        result = *handle == 0 ? -1 : 0;
    }

    ptrace(PTRACE_POKEDATA, pid, (void*)saved.pc, (void*)originalInstruction);
    set_registers(pid, &saved);
    return result;
}

int main(int argc, char** argv) {
    if (argc != 3) {
        fprintf(stderr, "usage: %s PID ABSOLUTE_LIBRARY_PATH\n", argv[0]);
        return 64;
    }
    char* end = NULL;
    long parsedPid = strtol(argv[1], &end, 10);
    if (end == argv[1] || *end != '\0' || parsedPid <= 1 || argv[2][0] != '/') return 64;
    pid_t pid = (pid_t)parsedPid;

    char linkerPath[PATH_MAX] = {0};
    uintptr_t linkerBias = module_load_bias(pid, "/linker64", linkerPath);
    uintptr_t symbol = dynamic_symbol_value(linkerPath, "__loader_dlopen");
    struct mapping callerMapping;
    if (linkerBias == 0 || symbol == 0 ||
        (read_mapping(pid, "/vendor/bin/hw/", true, &callerMapping) != 0 &&
         read_mapping(pid, NULL, true, &callerMapping) != 0)) {
        fprintf(stderr, "cannot resolve target linker or caller mapping\n");
        return 65;
    }

    if (ptrace(PTRACE_ATTACH, pid, NULL, NULL) != 0 || wait_for_stop(pid, SIGSTOP) != 0) {
        fprintf(stderr, "ptrace attach failed: %s\n", strerror(errno));
        return 66;
    }
    uintptr_t handle = 0;
    int callStatus = call_remote_dlopen(pid, linkerBias + symbol, callerMapping.start + 4,
                                        argv[2], &handle);
    ptrace(PTRACE_DETACH, pid, NULL, NULL);
    if (callStatus != 0) {
        fprintf(stderr, "remote dlopen failed\n");
        return 67;
    }

    const char* basename = strrchr(argv[2], '/');
    struct mapping loaded;
    if (read_mapping(pid, basename == NULL ? argv[2] : basename + 1, false, &loaded) != 0) {
        fprintf(stderr, "library handle returned but mapping was not found\n");
        return 68;
    }
    printf("injected=%s handle=0x%lx\n", loaded.path, (unsigned long)handle);
    return 0;
}
