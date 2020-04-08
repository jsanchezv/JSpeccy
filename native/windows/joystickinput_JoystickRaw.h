/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class joystickinput_JoystickRaw */

#ifndef _Included_joystickinput_JoystickRaw
#define _Included_joystickinput_JoystickRaw
#ifdef __cplusplus
extern "C" {
#endif
#undef joystickinput_JoystickRaw_EVENT_BUFFER
#define joystickinput_JoystickRaw_EVENT_BUFFER 64L
#undef joystickinput_JoystickRaw_STRUCT_EVENT_SIZE
#define joystickinput_JoystickRaw_STRUCT_EVENT_SIZE 8L
#undef joystickinput_JoystickRaw_JOYSTICK_BUFFER_SIZE
#define joystickinput_JoystickRaw_JOYSTICK_BUFFER_SIZE 512L
#undef joystickinput_JoystickRaw_TIMESTAMP_OFFSET
#define joystickinput_JoystickRaw_TIMESTAMP_OFFSET 0L
#undef joystickinput_JoystickRaw_VALUE_OFFSET
#define joystickinput_JoystickRaw_VALUE_OFFSET 4L
#undef joystickinput_JoystickRaw_TYPE_OFFSET
#define joystickinput_JoystickRaw_TYPE_OFFSET 6L
#undef joystickinput_JoystickRaw_NUMBER_OFFSET
#define joystickinput_JoystickRaw_NUMBER_OFFSET 7L
#undef joystickinput_JoystickRaw_JS_EVENT_BUTTON
#define joystickinput_JoystickRaw_JS_EVENT_BUTTON 1L
#undef joystickinput_JoystickRaw_JS_EVENT_AXIS
#define joystickinput_JoystickRaw_JS_EVENT_AXIS 2L
#undef joystickinput_JoystickRaw_JS_EVENT_INIT
#define joystickinput_JoystickRaw_JS_EVENT_INIT 128L
#undef joystickinput_JoystickRaw_JS_EVENT_INIT_BUTTON
#define joystickinput_JoystickRaw_JS_EVENT_INIT_BUTTON 129L
#undef joystickinput_JoystickRaw_JS_EVENT_INIT_AXIS
#define joystickinput_JoystickRaw_JS_EVENT_INIT_AXIS 130L
#undef joystickinput_JoystickRaw_MAX_BUTTONS
#define joystickinput_JoystickRaw_MAX_BUTTONS 32L
#undef joystickinput_JoystickRaw_MAX_AXIS
#define joystickinput_JoystickRaw_MAX_AXIS 32L
/*
 * Class:     joystickinput_JoystickRaw
 * Method:    getNumButtonsHelper
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_joystickinput_JoystickRaw_getNumButtonsHelper
  (JNIEnv *, jobject, jint);

/*
 * Class:     joystickinput_JoystickRaw
 * Method:    getNumAxisHelper
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_joystickinput_JoystickRaw_getNumAxisHelper
  (JNIEnv *, jobject, jint);

/*
 * Class:     joystickinput_JoystickRaw
 * Method:    toStringHelper
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_joystickinput_JoystickRaw_toStringHelper
  (JNIEnv *, jobject, jint);

#ifdef __cplusplus
}
#endif
#endif
