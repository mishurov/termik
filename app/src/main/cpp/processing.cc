#include <jni.h>
#include <string>
#include <fstream>
#include <thread>

#include <android/log.h>

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>


#define  LOG_TAG    "Termik"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)


static int g_output_pref = 0;


void Termik(cv::Mat *display_img)
{
	int low_threshold = 65;
	int ratio = 3;
	int kernel_size = 3;

	// Process image: red tint and edges
	cv::Mat img = display_img->clone();
	cv::cvtColor(img, img, cv::COLOR_RGBA2GRAY);
	cv::Mat edges;
	cv::blur(img, edges, cv::Size(3,3));
	Canny(edges, edges, low_threshold, low_threshold * ratio, kernel_size);

	cv::cvtColor(img, img, CV_GRAY2RGBA);
	cv::cvtColor(edges, edges, CV_GRAY2RGBA);
	cv::Scalar color;

	if (g_output_pref == 0) {
		color = cv::Scalar(1, 0.2, 0.2, 1);
	} else {
		color = cv::Scalar(0.2, 1, 0.2, 1);
	}

	img = img.mul(color);
	*display_img = img + edges;
}


extern "C"
{

void JNICALL Java_uk_co_mishurov_termik2_MainActivity_process(
				JNIEnv *env, jobject instance, jlong image)
{
	cv::Mat &img = *(cv::Mat *) image;
	Termik(&img);

}

void JNICALL Java_uk_co_mishurov_termik2_MainActivity_setprefs(
				JNIEnv * env, jobject obj, jint output)
{
	g_output_pref = output;
}


} // extern C

