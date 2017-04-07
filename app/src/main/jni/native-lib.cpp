#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/dnn.hpp>
#include <android/log.h>

#include <fstream>
#include <thread>
#include <atomic>

#define  LOG_TAG    "Termik"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

using namespace cv;
using namespace cv::dnn;

static char obbMountPath[256];
static char guess_first[256];
static std::atomic<int> is_inferring(0);
static std::thread infer;

/* Find best class for the blob (i. e. class with maximal probability) */
void getMaxClass(dnn::Blob &probBlob, int *classId, double *classProb)
{
    Mat probMat = probBlob.matRefConst().reshape(1, 1); //reshape the blob to 1x1000 matrix
    Point classNumber;

    minMaxLoc(probMat, NULL, classProb, NULL, &classNumber);
    *classId = classNumber.x;

	LOGI("Best class id: %i", *classId);
	LOGI("Probability: %f", *classProb);
	cv::sort(probMat, probMat, CV_SORT_EVERY_ROW + CV_SORT_DESCENDING);

    *classProb = probMat.at<double>(1,1);
	LOGI("Index probability: %f", *classProb);
	//LOGI("Index Probability: %f", *classProb);
}


std::vector<String> readClassNames(const char *filename)
{
    std::vector<String> classNames;

    std::ifstream fp(filename);
    if (!fp.is_open())
    {
        std::cerr << "File with classes labels not found: " << filename << std::endl;
        exit(-1);
    }

    std::string name;
    while (!fp.eof())
    {
        std::getline(fp, name);
        if (name.length())
            classNames.push_back( name );
    }

    fp.close();
    return classNames;
}

void incept(Mat img) {
	String modelFile;
	String labelsFile;
	modelFile += obbMountPath;
	labelsFile += obbMountPath;
	modelFile += "/tensorflow_inception_graph.pb";
	labelsFile += "/imagenet_comp_graph_label_strings.txt";

	Ptr<dnn::Importer> importer;
	try                                     //Try to import TensorFlow AlexNet model
    {
        importer = dnn::createTensorflowImporter(modelFile);
    }
    catch (const cv::Exception &err)        //Importer can throw errors, we will catch them
    {
		LOGI("Import Exception");
        std::cerr << err.msg << std::endl;
    }
	if (!importer)
    {
		LOGI("Load error");
        std::cerr << "Can't load network by using the mode file: " << std::endl;
        std::cerr << modelFile << std::endl;
        exit(-1);
    }
	 //! [Initialize network]
    dnn::Net net;
    importer->populateNet(net);
    importer.release();

	cv::Size inputImgSize = cv::Size(224, 224);
    resize(img, img, inputImgSize);  
	cv::cvtColor(img, img, cv::COLOR_RGBA2RGB);


	dnn::Blob inputBlob = dnn::Blob::fromImages(img);   //Convert Mat to dnn::Blob image batch
    //! [Prepare blob]

    //! [Set input blob]

	String inBlobName = ".input";
    net.setBlob(inBlobName, inputBlob);        //set the network input
    //! [Set input blob]

	LOGI("Starting computation");
    cv::TickMeter tm;
    tm.start();

    //! [Make forward pass]
    net.forward();                          //compute output
    //! [Make forward pass]

    tm.stop();

    //! [Gather output]
	String outBlobName = "softmax2";
    dnn::Blob prob = net.getBlob(outBlobName);   //gather output of "prob" layer

    //Mat& result = prob.matRef();

    BlobShape shape = prob.shape();

	if (!labelsFile.empty()) {
        std::vector<String> classNames = readClassNames(labelsFile.c_str());

        int classId;
        double classProb;
        getMaxClass(prob, &classId, &classProb);//find the best class

        //! [Print results]
        std::cout << "Best class: #" << classId << " '" << classNames.at(classId) << "'" << std::endl;
        std::cout << "Probability: " << classProb * 100 << "%" << std::endl;
		LOGI("Best class: %s", classNames.at(classId).c_str());
		LOGI("Probability: %f", classProb);
		sprintf(guess_first, "%s %f", classNames.at(classId).c_str(), classProb);
    }
	is_inferring = 0;
}

int edgeThresh = 1;
int lowThreshold = 65;
int const max_lowThreshold = 100;
int ratio = 3;
int kernel_size = 3;

void termik(Mat *real_img) {
	Mat img = real_img->clone();
	cv::cvtColor(img, img, cv::COLOR_RGBA2GRAY);
	Mat edges;
	cv::blur(img, edges, Size(3,3));
	Canny(edges, edges, lowThreshold, lowThreshold*ratio, kernel_size );

	cvtColor(img, img, CV_GRAY2RGBA);
	cvtColor(edges, edges, CV_GRAY2RGBA);
	cv::Scalar colorScalar = cv::Scalar(1, 0.2, 0.2, 1);
	img = img.mul(colorScalar);
	*real_img = img + edges;

	//cvtColor(detected_edges, *real_img, CV_GRAY2RGBA);
}

extern "C"
{
void JNICALL Java_uk_co_mishurov_termik_MainActivity_salt(
	JNIEnv *env, jobject instance, jlong image) {
    Mat &img = *(Mat *) image;
	if(obbMountPath[0] != '\0') {
		termik(&img);
		/*
		if(is_inferring == 0) {
			if(infer.joinable()) {
				infer.join();
				jstring s = env->NewStringUTF(guess_first);
				jclass clazz = env->FindClass("uk/co/mishurov/termik/MainActivity");
				jmethodID mid = env->GetMethodID(
					clazz, "setInference", "(Ljava/lang/String;)V"
				);
				env->CallVoidMethod(instance, mid, s);
				env->DeleteLocalRef(s);
			}
			is_inferring = 1;
			infer = std::thread(incept, img.clone());
		}
		*/
	}
}

void JNICALL Java_uk_co_mishurov_termik_MainActivity_setdir(
	JNIEnv *env, jobject instance, jstring path) {
	strcpy(obbMountPath, (char*)env->GetStringUTFChars(path, NULL));
}

}

