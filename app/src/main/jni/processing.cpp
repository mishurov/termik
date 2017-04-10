#include <jni.h>
#include <string>
#include <fstream>
#include <thread>
#include <atomic>

#include <android/log.h>

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/dnn.hpp>

#define  LOG_TAG    "Termik"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)


static char obbMountPath[256];
static char guess[256];
// Inference in a separate thread
static std::atomic<int> isInferring(0);
static std::thread infer;


void getMaxClass(cv::dnn::Blob &probBlob, int *classId, double *classProb) {
	// Reshape the blob to 1x1000 matrix
	cv::Mat probMat = probBlob.matRefConst().reshape(1, 1);
	cv::Point classNumber;
	minMaxLoc(probMat, NULL, classProb, NULL, &classNumber);
	*classId = classNumber.x;
}


std::vector<std::string> readClassNames(const char *filename) {
	std::vector<std::string> classNames;

	std::ifstream fp(filename);
	if (!fp.is_open()) {
		std::cerr << "File with classes labels not found: " << filename << std::endl;
		exit(-1);
	}

	std::string name;
	while (!fp.eof()) {
		std::getline(fp, name);
		if (name.length())
			classNames.push_back( name );
	}

	fp.close();
	return classNames;
}


void incept(cv::Mat img) {
	std::string modelFile;
	std::string labelsFile;
	modelFile += obbMountPath;
	labelsFile += obbMountPath;
	modelFile += "/tensorflow_inception_graph.pb";
	labelsFile += "/imagenet_comp_graph_label_strings.txt";

	cv::Ptr<cv::dnn::Importer> importer;
	try {
		importer = cv::dnn::createTensorflowImporter(modelFile);
	}
	catch (const cv::Exception &err) {
		LOGI("Import Exception");
		std::cerr << err.msg << std::endl;
	}
	if (!importer) {
		LOGI("Load error");
		std::cerr << "Can't load network by using the mode file: " << std::endl;
		std::cerr << modelFile << std::endl;
		exit(-1);
	}
	// Initialize network
	cv::dnn::Net net;
	importer->populateNet(net);
	importer.release();

	cv::Size inputImgSize = cv::Size(224, 224);
	resize(img, img, inputImgSize);  
	cv::cvtColor(img, img, cv::COLOR_RGBA2RGB);

	// Convert Mat to dnn::Blob image batch
	cv::dnn::Blob inputBlob = cv::dnn::Blob::fromImages(img);

	std::string inBlobName = ".input";
	net.setBlob(inBlobName, inputBlob);

	cv::TickMeter tm;
	tm.start();
	// Make forward pass
	net.forward();
	tm.stop();

	// Gather output
	std::string outBlobName = "softmax2";
	cv::dnn::Blob prob = net.getBlob(outBlobName);

	cv::dnn::BlobShape shape = prob.shape();

	if (!labelsFile.empty()) {
		std::vector<std::string> classNames = readClassNames(labelsFile.c_str());

		int classId;
		double classProb;
		// Find the best class
		getMaxClass(prob, &classId, &classProb);

		// Print results
		std::cout << "Best class: #" << classId << " '" << classNames.at(classId) << "'" << std::endl;
		std::cout << "Probability: " << classProb * 100 << "%" << std::endl;
		LOGI("Best class: %s", classNames.at(classId).c_str());
		LOGI("Probability: %f", classProb);
		sprintf(guess, "%s %f", classNames.at(classId).c_str(), classProb);
	}
	isInferring = 0;
}


int lowThreshold = 65;
int ratio = 3;
int kernelSize = 3;

void termik(cv::Mat *display_img) {
	// Process image: red tint and edges
	cv::Mat img = display_img->clone();
	cv::cvtColor(img, img, cv::COLOR_RGBA2GRAY);
	cv::Mat edges;
	cv::blur(img, edges, cv::Size(3,3));
	Canny(edges, edges, lowThreshold, lowThreshold*ratio, kernelSize );

	cv::cvtColor(img, img, CV_GRAY2RGBA);
	cv::cvtColor(edges, edges, CV_GRAY2RGBA);
	cv::Scalar colorScalar = cv::Scalar(1, 0.2, 0.2, 1);
	img = img.mul(colorScalar);
	*display_img = img + edges;
}


// JNI
extern "C"
{

// Process the image from camera
void JNICALL Java_uk_co_mishurov_termik_MainActivity_salt(
	JNIEnv *env, jobject instance, jlong image) {
	cv::Mat &img = *(cv::Mat *) image;
	termik(&img);

	/*
	if(obbMountPath[0] != '\0') {
		// infer image in a separate thread and set result in Java code
		if(isInferring == 0) {
			if(infer.joinable()) {
				infer.join();
				jstring s = env->NewStringUTF(guess);
				jclass activityClass = env->FindClass("uk/co/mishurov/termik/MainActivity");
				jmethodID methodId = env->GetMethodID(
					activityClass, "setInference", "(Ljava/lang/String;)V"
				);
				env->CallVoidMethod(instance, methodId, s);
				env->DeleteLocalRef(s);
			}
			isInferring = 1;
			infer = std::thread(incept, img.clone());
		}
	}
	*/
}

// Set mounted directory with the model
void JNICALL Java_uk_co_mishurov_termik_MainActivity_setdir(
	JNIEnv *env, jobject instance, jstring path) {
	strcpy(obbMountPath, (char*)env->GetStringUTFChars(path, NULL));
}

}

