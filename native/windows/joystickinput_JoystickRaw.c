#include "joystickinput_JoystickRaw.h"

// Dummy loopback to check gradle build shared library

JNIEXPORT jint JNICALL Java_joystickinput_JoystickRaw_getNumButtonsHelper
  (JNIEnv *env, jobject obj, jint joystickId) {

	return -1;
}

JNIEXPORT jint JNICALL Java_joystickinput_JoystickRaw_getNumAxisHelper
  (JNIEnv *env, jobject obj, jint joystickId) {

	return -1;
}

JNIEXPORT jstring JNICALL Java_joystickinput_JoystickRaw_toStringHelper
  (JNIEnv *env, jobject obj, jint joystickId) {
	
    return (*env)->NewStringUTF(env, "Dummy");
}
