#include <sys/stat.h>

#include <android/storage_manager.h>
#include <android/log.h>

#define  LOG_TAG    "Termik"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

void my_obbCallbackFunc(const char* filename, const int32_t state, void* data)
{
	LOGI("my_obbCallbackFunc: %d", state);

	AStorageManager* man = AStorageManager_new();
	int isMounted = AStorageManager_isObbMounted(man, filename);
	const char* mntPath = AStorageManager_getMountedObbPath(man, filename);

	LOGI("my_obbCallbackFunc: fn: %s: mounted path: %s, already mounted?: %d", filename, mntPath, isMounted);
	AStorageManager_delete(man);
}

void obb(JNIEnv *env, jobject instance) {

	char obbPath[256];
	sprintf(obbPath, "/mnt/sdcard/Android/obb/uk.co.mishurov.termik/main.1.uk.co.mishurov.termik2.obb");
	struct stat sts;
	if(stat(obbPath, &sts) == -1)
	{
		LOGI("File not found: %s\n", obbPath);
	}
	else
	{
		LOGI("File found: %s", obbPath);
	}

	AStorageManager* man = AStorageManager_new();
	char* data = (char*)malloc(256);
    LOGI("unmount");
	AStorageManager_unmountObb(man, obbPath, 1, my_obbCallbackFunc, data);
    LOGI("mount");
	AStorageManager_mountObb(man, obbPath, NULL, my_obbCallbackFunc, data);
	const char* mntPath = AStorageManager_getMountedObbPath(man, obbPath);

	int isMounted = AStorageManager_isObbMounted(man, obbPath);

	LOGI("mounted path: %s, already mounted?: %d", mntPath, isMounted);
	free(data);
	data = NULL;
	AStorageManager_delete(man);
	man = NULL;
}

