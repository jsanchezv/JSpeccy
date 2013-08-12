#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <string.h>
#include <linux/joystick.h>

#include "joystickinput_JoystickRaw.h"

int ioctlRequestChar(int, int);

JNIEXPORT jint JNICALL Java_joystickinput_JoystickRaw_getNumButtonsHelper
  (JNIEnv *env, jobject obj, jint joystickId) {

	if (joystickId < 0 || joystickId > 3)
		return -1;

	return ioctlRequestChar(joystickId, JSIOCGBUTTONS);
}

JNIEXPORT jint JNICALL Java_joystickinput_JoystickRaw_getNumAxisHelper
  (JNIEnv *env, jobject obj, jint joystickId) {

	if (joystickId < 0 || joystickId > 3)
		return -1;

	return ioctlRequestChar(joystickId, JSIOCGAXES);
}

JNIEXPORT jstring JNICALL Java_joystickinput_JoystickRaw_toStringHelper
  (JNIEnv *env, jobject obj, jint joystickId) {
	
	if (joystickId < 0 || joystickId > 3)
		return (*env)->NewStringUTF(env, NULL);

	char devName[32];
	int fd;

	sprintf(devName, "/dev/input/js%d", joystickId);
	if ((fd = open(devName, O_RDONLY)) < 0)
		return (*env)->NewStringUTF(env, NULL);

	char joyName[128];
	int size = ioctl(fd, JSIOCGNAME(sizeof(joyName)), joyName);
	close(fd);
	if (size < 1)
		strcpy(joyName, "Unknown joystick model");

	return (*env)->NewStringUTF(env, joyName);
}

int ioctlRequestChar(int id, int request) {

	char devName[32];
	int fd, err;
	char result;

	sprintf(devName, "/dev/input/js%d", id);
	if ((fd = open(devName, O_RDONLY)) < 0)
		return -1;

	err = ioctl(fd, request, &result);
	close(fd);

	return err < 0 ? -1 : result;
}
