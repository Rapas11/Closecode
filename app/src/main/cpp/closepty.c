/*
 * closepty — the smallest PTY bridge CloseCode needs.
 *
 * The phone's own shell must believe it is talking to a terminal: programs then colour
 * their output, Ctrl+C raises SIGINT, and the window size is real. Bionic ships
 * posix_openpt/grantpt/unlockpt/ptsname but not forkpty, so the fork/setsid/TIOCSCTTY
 * dance is done here.
 *
 * Echo is turned off on the slave: the transcript panel draws the prompt and the typed
 * command itself, so a kernel echo would print every line twice. ISIG and ICANON stay on,
 * which is what makes Ctrl+C a real interrupt instead of a byte written into a pipe.
 */
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

static char **to_strings(JNIEnv *env, jobjectArray array) {
    if (array == NULL) {
        char **empty = (char **) calloc(1, sizeof(char *));
        return empty;
    }
    jsize count = (*env)->GetArrayLength(env, array);
    char **out = (char **) calloc((size_t) count + 1, sizeof(char *));
    if (out == NULL) return NULL;
    for (jsize i = 0; i < count; i++) {
        jstring string = (jstring) (*env)->GetObjectArrayElement(env, array, i);
        if (string == NULL) {
            out[i] = strdup("");
            continue;
        }
        const char *chars = (*env)->GetStringUTFChars(env, string, NULL);
        out[i] = chars == NULL ? strdup("") : strdup(chars);
        if (chars != NULL) (*env)->ReleaseStringUTFChars(env, string, chars);
        (*env)->DeleteLocalRef(env, string);
    }
    out[count] = NULL;
    return out;
}

static void free_strings(char **strings) {
    if (strings == NULL) return;
    for (char **cursor = strings; *cursor != NULL; cursor++) free(*cursor);
    free(strings);
}

static void set_winsize(int fd, int rows, int cols) {
    struct winsize size;
    memset(&size, 0, sizeof(size));
    size.ws_row = (unsigned short) (rows > 0 ? rows : 24);
    size.ws_col = (unsigned short) (cols > 0 ? cols : 80);
    ioctl(fd, TIOCSWINSZ, &size);
}

JNIEXPORT jint JNICALL
Java_com_closecode_ui_ide_Pty_createSubprocess(
        JNIEnv *env,
        jobject self,
        jstring command,
        jstring workingDirectory,
        jobjectArray argvArray,
        jobjectArray envArray,
        jintArray pidOut,
        jint rows,
        jint cols) {
    (void) self;

    const char *cmd = (*env)->GetStringUTFChars(env, command, NULL);
    const char *cwd = (*env)->GetStringUTFChars(env, workingDirectory, NULL);
    char **argv = to_strings(env, argvArray);
    char **envp = to_strings(env, envArray);
    if (cmd == NULL || cwd == NULL || argv == NULL || envp == NULL) {
        if (cmd != NULL) (*env)->ReleaseStringUTFChars(env, command, cmd);
        if (cwd != NULL) (*env)->ReleaseStringUTFChars(env, workingDirectory, cwd);
        free_strings(argv);
        free_strings(envp);
        return -1;
    }

    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0) {
        (*env)->ReleaseStringUTFChars(env, command, cmd);
        (*env)->ReleaseStringUTFChars(env, workingDirectory, cwd);
        free_strings(argv);
        free_strings(envp);
        return -1;
    }
    if (grantpt(master) != 0 || unlockpt(master) != 0) {
        close(master);
        (*env)->ReleaseStringUTFChars(env, command, cmd);
        (*env)->ReleaseStringUTFChars(env, workingDirectory, cwd);
        free_strings(argv);
        free_strings(envp);
        return -1;
    }
    const char *slaveName = ptsname(master);
    if (slaveName == NULL) {
        close(master);
        (*env)->ReleaseStringUTFChars(env, command, cmd);
        (*env)->ReleaseStringUTFChars(env, workingDirectory, cwd);
        free_strings(argv);
        free_strings(envp);
        return -1;
    }
    int slave = open(slaveName, O_RDWR | O_NOCTTY);
    if (slave < 0) {
        close(master);
        (*env)->ReleaseStringUTFChars(env, command, cmd);
        (*env)->ReleaseStringUTFChars(env, workingDirectory, cwd);
        free_strings(argv);
        free_strings(envp);
        return -1;
    }

    set_winsize(slave, rows, cols);

    pid_t pid = fork();
    if (pid < 0) {
        close(slave);
        close(master);
        (*env)->ReleaseStringUTFChars(env, command, cmd);
        (*env)->ReleaseStringUTFChars(env, workingDirectory, cwd);
        free_strings(argv);
        free_strings(envp);
        return -1;
    }

    if (pid == 0) {
        // Child: a new session with the slave as its controlling terminal.
        setsid();
        ioctl(slave, TIOCSCTTY, 0);
        struct termios term;
        if (tcgetattr(slave, &term) == 0) {
            term.c_lflag &= ~(tcflag_t) ECHO;
            term.c_lflag |= (tcflag_t) (ISIG | ICANON | IEXTEN);
            term.c_iflag |= (tcflag_t) ICRNL;
            term.c_oflag |= (tcflag_t) (OPOST | ONLCR);
            term.c_cc[VINTR] = 3;
            term.c_cc[VERASE] = 127;
            tcsetattr(slave, TCSANOW, &term);
        }
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);
        if (master > STDERR_FILENO) close(master);
        if (chdir(cwd) != 0) _exit(126);
        execve(cmd, argv, envp);
        _exit(127);
    }

    close(slave);
    int out[1];
    out[0] = (int) pid;
    (*env)->SetIntArrayRegion(env, pidOut, 0, 1, out);
    (*env)->ReleaseStringUTFChars(env, command, cmd);
    (*env)->ReleaseStringUTFChars(env, workingDirectory, cwd);
    free_strings(argv);
    free_strings(envp);
    return master;
}

JNIEXPORT void JNICALL
Java_com_closecode_ui_ide_Pty_setPtyWindowSize(
        JNIEnv *env, jobject self, jint fd, jint rows, jint cols) {
    (void) env;
    (void) self;
    if (fd >= 0) set_winsize(fd, rows, cols);
}

JNIEXPORT jboolean JNICALL
Java_com_closecode_ui_ide_Pty_isAlive(JNIEnv *env, jobject self, jint pid) {
    (void) env;
    (void) self;
    if (pid <= 0) return JNI_FALSE;
    if (kill((pid_t) pid, 0) == 0) return JNI_TRUE;
    return errno == EPERM ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_closecode_ui_ide_Pty_waitFor(JNIEnv *env, jobject self, jint pid) {
    (void) env;
    (void) self;
    int status = 0;
    while (waitpid((pid_t) pid, &status, 0) < 0) {
        if (errno != EINTR) return -1;
    }
    return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

JNIEXPORT void JNICALL
Java_com_closecode_ui_ide_Pty_closeFd(JNIEnv *env, jobject self, jint fd) {
    (void) env;
    (void) self;
    if (fd >= 0) close(fd);
}

JNIEXPORT void JNICALL
Java_com_closecode_ui_ide_Pty_hangup(JNIEnv *env, jobject self, jint pid) {
    (void) env;
    (void) self;
    if (pid > 0) kill((pid_t) pid, SIGHUP);
}
